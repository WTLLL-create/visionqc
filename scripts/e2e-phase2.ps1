# VisionQC Phase 2 端到端验收脚本
# 流程：管理员登录 → 建设备/批次 → 上传 PNG → 质检员保存标注 → 直接校验
#       → 重启 MySQL/后端容器 → 重新登录校验文件 SHA-256 与标注仍在（Docker 卷持久化）
# 依赖：Docker Desktop 中 vqc 容器已启动；curl.exe；PowerShell 7
$ErrorActionPreference = "Stop"
$base = "http://localhost:18080/api/v1"
$adminPassword = if ($env:VQC_ADMIN_PASSWORD) { $env:VQC_ADMIN_PASSWORD } else { "change-me-admin" }
$inspectorPassword = if ($env:VQC_INSPECTOR_PASSWORD) { $env:VQC_INSPECTOR_PASSWORD } else { "change-me-inspector" }
$adminJar = Join-Path $env:TEMP "vqc-e2e-admin.txt"
$inspJar = Join-Path $env:TEMP "vqc-e2e-inspector.txt"
Remove-Item -LiteralPath $adminJar, $inspJar -ErrorAction SilentlyContinue

function Write-JsonFile([string]$json) {
    $path = Join-Path $env:TEMP ("vqc-body-" + [guid]::NewGuid().ToString("N") + ".json")
    [IO.File]::WriteAllText($path, $json, (New-Object Text.UTF8Encoding($false)))
    return $path
}

function Get-Csrf([string]$jar) {
    $raw = & curl.exe -s -c $jar "$base/auth/csrf"
    $obj = $raw | ConvertFrom-Json
    if (-not $obj.token) { throw "获取 CSRF token 失败: $raw" }
    return $obj.token
}

function Invoke-Json([string]$method, [string]$url, [string]$jar, [string]$token, [string]$json) {
    $bodyFile = Write-JsonFile $json
    $raw = & curl.exe -s -X $method -b $jar -c $jar `
        -H "Content-Type: application/json" -H "X-XSRF-TOKEN: $token" `
        --data-binary "@$bodyFile" $url
    Remove-Item -LiteralPath $bodyFile -ErrorAction SilentlyContinue
    return $raw
}

function Login([string]$jar, [string]$user, [string]$pwd) {
    $token = Get-Csrf $jar
    $json = "{`"username`":`"$user`",`"password`":`"$pwd`"}"
    $resp = Invoke-Json "POST" "$base/auth/login" $jar $token $json
    $obj = $resp | ConvertFrom-Json
    if ($obj.username -ne $user) { throw "登录失败 $user : $resp" }
    Write-Host "PASS 登录 $user"
    return $token
}

function Wait-Backend([int]$timeoutSec = 90) {
    $deadline = (Get-Date).AddSeconds($timeoutSec)
    do {
        Start-Sleep -Seconds 3
        try {
            $r = & curl.exe -s -o NUL -w "%{http_code}" "$base/auth/csrf"
            if ($r -eq "200") { return }
        } catch { }
    } while ((Get-Date) -lt $deadline)
    throw "后端在 $timeoutSec 秒内未恢复"
}

$suffix = Get-Date -Format "HHmmss"
$png = Join-Path $env:TEMP "vqc-e2e-part.png"
if (-not (Test-Path -LiteralPath $png)) {
    Add-Type -AssemblyName System.Drawing
    $bmp = New-Object System.Drawing.Bitmap 800, 600
    $g = [System.Drawing.Graphics]::FromImage($bmp)
    $g.Clear([System.Drawing.Color]::FromArgb(40, 44, 52))
    $g.DrawRectangle([System.Drawing.Pens]::OrangeRed, 100, 100, 240, 160)
    $bmp.Save($png, [System.Drawing.Imaging.ImageFormat]::Png)
    $g.Dispose(); $bmp.Dispose()
}

# 1. 管理员建批次
$adminToken = Login $adminJar "admin" $adminPassword
$deviceJson = "{`"deviceName`":`"E2E相机-$suffix`",`"deviceCode`":`"CAM-E2E-$suffix`",`"description`":`"端到端验收`"}"
$device = (Invoke-Json "POST" "$base/devices" $adminJar $adminToken $deviceJson) | ConvertFrom-Json
Write-Host "PASS 创建设备 id=$($device.id)"
$batchJson = "{`"batchCode`":`"B-E2E-$suffix`",`"deviceId`":$($device.id),`"productType`":`"E2E-PART`",`"captureDate`":`"$(Get-Date -Format 'yyyy-MM-dd')`"}"
$batch = (Invoke-Json "POST" "$base/batches" $adminJar $adminToken $batchJson) | ConvertFrom-Json
Write-Host "PASS 创建批次 id=$($batch.id) code=$($batch.batchCode)"

# 2. 上传图片
$uploadRaw = & curl.exe -s -b $adminJar -c $adminJar -H "X-XSRF-TOKEN: $adminToken" `
    -F "files=@$png;type=image/png" "$base/images/upload/$($batch.id)"
$upload = ($uploadRaw | ConvertFrom-Json)[0]
if (-not $upload.success) { throw "上传失败: $uploadRaw" }
$imageId = $upload.imageId
$taskId = $upload.taskId
Write-Host "PASS 上传图片 imageId=$imageId taskId=$taskId"

# 3. Phase 3 起标注需要真实归属：管理员分配质检员，质检员开始任务
$users = (& curl.exe -s -b $adminJar "$base/users?role=INSPECTOR&keyword=inspector01&page=0&size=10") | ConvertFrom-Json
$inspectorId = ($users.content | Where-Object { $_.username -eq "inspector01" } | Select-Object -First 1).id
if (-not $inspectorId) { throw "未找到 inspector01 用户: $($users | ConvertTo-Json -Depth 4)" }
$assignJson = "{`"inspectorId`":$inspectorId,`"version`":0,`"comment`":`"Phase 2.5/3 回归分配`"}"
$assigned = (Invoke-Json "POST" "$base/tasks/$taskId/assign" $adminJar $adminToken $assignJson) | ConvertFrom-Json
if ($assigned.status -ne "ASSIGNED") { throw "分配失败: $($assigned | ConvertTo-Json -Depth 4)" }
Write-Host "PASS 分配任务 inspectorId=$inspectorId version=$($assigned.version)"

# 4. 质检员保存标注（模拟前端画框后的归一化请求）
$inspToken = Login $inspJar "inspector01" $inspectorPassword
$startJson = "{`"version`":$($assigned.version),`"comment`":`"开始标注`"}"
$started = (Invoke-Json "POST" "$base/tasks/$taskId/start" $inspJar $inspToken $startJson) | ConvertFrom-Json
if ($started.status -ne "IN_PROGRESS") { throw "开始任务失败: $($started | ConvertTo-Json -Depth 4)" }
Write-Host "PASS 开始任务 status=$($started.status) version=$($started.version)"
$annJson = '{"annotations":[{"categoryId":1,"x":0.125,"y":0.166667,"w":0.300000,"h":0.266667,"imgWidth":800,"imgHeight":600,"note":"E2E 划痕"}]}'
$annRaw = Invoke-Json "PUT" "$base/tasks/$taskId/annotations" $inspJar $inspToken $annJson
$ann = ($annRaw | ConvertFrom-Json)[0]
if ([math]::Abs($ann.xNorm - 0.125) -gt 0.000001) { throw "坐标保存不一致: $annRaw" }
Write-Host "PASS 标注保存 xNorm=$($ann.xNorm) wNorm=$($ann.wNorm)"

$readBack = (& curl.exe -s -b $inspJar "$base/tasks/$taskId/annotations") | ConvertFrom-Json
if ($readBack.Count -ne 1 -or [math]::Abs($readBack[0].xNorm - 0.125) -gt 0.000001) {
    throw "标注回读失败: $($readBack | ConvertTo-Json -Depth 4)"
}
Write-Host "PASS 标注回读一致 note=$($readBack[0].note)"

# 4. 重启容器验证 Docker 卷持久化
Write-Host "INFO 重启 MySQL 与后端容器 ..."
Set-Location -LiteralPath (Split-Path -Parent $PSScriptRoot)
& docker compose restart mysql | Out-Null
$deadline = (Get-Date).AddSeconds(90)
do {
    Start-Sleep -Seconds 3
    $state = & docker inspect --format "{{.State.Health.Status}}" vqc-mysql
} while ($state -ne "healthy" -and (Get-Date) -lt $deadline)
if ($state -ne "healthy") { throw "MySQL 重启后未恢复健康" }
& docker compose restart backend | Out-Null
Wait-Backend

# 5. 重启后重新登录，校验文件与标注
$adminToken = Login $adminJar "admin" $adminPassword
$outPng = Join-Path $env:TEMP "vqc-e2e-download.png"
& curl.exe -s -b $adminJar -o $outPng "$base/images/$imageId/file"
$srcHash = (Get-FileHash -LiteralPath $png -Algorithm SHA256).Hash.ToLower()
$dstHash = (Get-FileHash -LiteralPath $outPng -Algorithm SHA256).Hash.ToLower()
if ($srcHash -ne $dstHash) { throw "重启后图片文件内容不一致: $srcHash vs $dstHash" }
Write-Host "PASS 重启后图片可读且 SHA-256 一致 $dstHash"

$inspToken = Login $inspJar "inspector01" $inspectorPassword
$after = (& curl.exe -s -b $inspJar "$base/tasks/$taskId/annotations") | ConvertFrom-Json
if ($after.Count -ne 1 -or [math]::Abs($after[0].xNorm - 0.125) -gt 0.000001) {
    throw "重启后标注丢失: $($after | ConvertTo-Json -Depth 4)"
}
Write-Host "PASS 重启后标注仍在 xNorm=$($after[0].xNorm)"

# 6. 重复上传幂等校验
$dupRaw = & curl.exe -s -b $adminJar -H "X-XSRF-TOKEN: $adminToken" `
    -F "files=@$png;type=image/png" "$base/images/upload/$($batch.id)"
$dup = ($dupRaw | ConvertFrom-Json)[0]
if (-not $dup.duplicate) { throw "重复上传未标记 duplicate: $dupRaw" }
Write-Host "PASS 重复上传幂等 duplicate=$($dup.duplicate) imageId=$($dup.imageId)"

Write-Host "E2E_RESULT=PASS imageId=$imageId taskId=$taskId batchId=$($batch.id)"
