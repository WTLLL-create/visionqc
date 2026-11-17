# VisionQC Phase 3 审核工作流端到端验收
# 覆盖：建批次 → 上传 → 分配 → 标注 → 提交 → 驳回 → 修改 → 再提交 → 通过 → 历史
#       + 权限矩阵（质检员不能审批 / 审核员不能改标注 / 管理员不能审批）
#       + 双审核员并发审批（真实 HTTP + MySQL 乐观锁，期望 200/409 各一个且只写一条 APPROVE）
# 依赖：Docker 中 vqc 容器已启动；curl.exe；PowerShell 7
$ErrorActionPreference = "Stop"
$base = "http://localhost:18080/api/v1"
$adminPassword = if ($env:VQC_ADMIN_PASSWORD) { $env:VQC_ADMIN_PASSWORD } else { "change-me-admin" }
$inspectorPassword = if ($env:VQC_INSPECTOR_PASSWORD) { $env:VQC_INSPECTOR_PASSWORD } else { "change-me-inspector" }
$reviewerPassword = if ($env:VQC_REVIEWER_PASSWORD) { $env:VQC_REVIEWER_PASSWORD } else { "change-me-reviewer" }
$temp = $env:TEMP
$adminJar = Join-Path $temp "vqc-p3-admin.txt"
$inspJar = Join-Path $temp "vqc-p3-inspector.txt"
$rev1Jar = Join-Path $temp "vqc-p3-reviewer1.txt"
$rev2Jar = Join-Path $temp "vqc-p3-reviewer2.txt"
Remove-Item -LiteralPath $adminJar, $inspJar, $rev1Jar, $rev2Jar -ErrorAction SilentlyContinue

function Write-JsonFile([string]$json) {
    $path = Join-Path $env:TEMP ("vqc-p3-body-" + [guid]::NewGuid().ToString("N") + ".json")
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

function Invoke-Status([string]$method, [string]$url, [string]$jar, [string]$token, [string]$json) {
    $bodyFile = Write-JsonFile $json
    $respFile = Join-Path $env:TEMP ("vqc-p3-resp-" + [guid]::NewGuid().ToString("N") + ".json")
    $code = & curl.exe -s -o $respFile -w "%{http_code}" -X $method -b $jar -c $jar `
        -H "Content-Type: application/json" -H "X-XSRF-TOKEN: $token" `
        --data-binary "@$bodyFile" $url
    $body = if (Test-Path -LiteralPath $respFile) { Get-Content -LiteralPath $respFile -Raw } else { "" }
    Remove-Item -LiteralPath $bodyFile, $respFile -ErrorAction SilentlyContinue
    return [pscustomobject]@{ code = [int]$code; body = $body }
}

function Login([string]$jar, [string]$user, [string]$pwd) {
    $token = Get-Csrf $jar
    $json = "{`"username`":`"$user`",`"password`":`"$pwd`"}"
    $obj = (Invoke-Json "POST" "$base/auth/login" $jar $token $json) | ConvertFrom-Json
    if ($obj.username -ne $user) { throw "登录失败 $user : $($obj | ConvertTo-Json -Depth 4)" }
    Write-Host "PASS 登录 $user"
    return $token
}

function Wait-Backend([int]$timeoutSec = 120) {
    $deadline = (Get-Date).AddSeconds($timeoutSec)
    do {
        Start-Sleep -Seconds 2
        try {
            $r = & curl.exe -s -o NUL -w "%{http_code}" "$base/auth/csrf"
            if ($r -eq "200") { return }
        } catch { }
    } while ((Get-Date) -lt $deadline)
    throw "后端在 $timeoutSec 秒内未恢复"
}

function New-Png([string]$path, [int]$r, [int]$g, [int]$b, [string]$text) {
    if (Test-Path -LiteralPath $path) { return }
    Add-Type -AssemblyName System.Drawing
    $bmp = New-Object System.Drawing.Bitmap 800, 600
    $gfx = [System.Drawing.Graphics]::FromImage($bmp)
    $gfx.Clear([System.Drawing.Color]::FromArgb($r, $g, $b))
    $font = New-Object System.Drawing.Font("Arial", 28)
    $gfx.DrawString($text, $font, [System.Drawing.Brushes]::White, 120, 260)
    $bmp.Save($path, [System.Drawing.Imaging.ImageFormat]::Png)
    $font.Dispose(); $gfx.Dispose(); $bmp.Dispose()
}

function Upload-Image([string]$path, [long]$batchId) {
    $raw = & curl.exe -s -b $adminJar -c $adminJar -H "X-XSRF-TOKEN: $adminToken" `
        -F "files=@$path;type=image/png" "$base/images/upload/$batchId"
    $item = ($raw | ConvertFrom-Json)[0]
    if (-not $item.success) { throw "上传失败: $raw" }
    return $item
}

function Assign-Task([long]$taskId, [long]$inspectorId, [int]$version) {
    $json = "{`"inspectorId`":$inspectorId,`"version`":$version,`"comment`":`"E2E 分配`"}"
    return (Invoke-Json "POST" "$base/tasks/$taskId/assign" $adminJar $adminToken $json) | ConvertFrom-Json
}

function Start-CurlProcess([string]$jar, [string]$token, [string]$outFile, [string]$url, [string]$json) {
    $bodyFile = Write-JsonFile $json
    $psi = [System.Diagnostics.ProcessStartInfo]::new()
    $psi.FileName = "curl.exe"
    foreach ($a in @("-s", "-o", $outFile, "-w", "%{http_code}", "-X", "POST", "-b", $jar, "-c", $jar,
                     "-H", "Content-Type: application/json", "-H", "X-XSRF-TOKEN: $token",
                     "--data-binary", "@$bodyFile", $url)) {
        $psi.ArgumentList.Add($a)
    }
    $psi.UseShellExecute = $false
    $psi.CreateNoWindow = $true
    $psi.RedirectStandardOutput = $true
    return [System.Diagnostics.Process]::Start($psi)
}

Wait-Backend
$suffix = Get-Date -Format "HHmmss"
$pngA = Join-Path $env:TEMP "vqc-p3-part-a.png"
$pngB = Join-Path $env:TEMP "vqc-p3-part-b.png"
New-Png $pngA 40 44 52 "PART-A"
New-Png $pngB 52 44 40 "PART-B"

# ---------- 1. 管理员建批次并上传 ----------
$adminToken = Login $adminJar "admin" $adminPassword
$deviceJson = "{`"deviceName`":`"P3相机-$suffix`",`"deviceCode`":`"CAM-P3-$suffix`",`"description`":`"Phase3 E2E`"}"
$device = (Invoke-Json "POST" "$base/devices" $adminJar $adminToken $deviceJson) | ConvertFrom-Json
$batchJson = "{`"batchCode`":`"B-P3-$suffix`",`"deviceId`":$($device.id),`"productType`":`"P3-PART`",`"captureDate`":`"$(Get-Date -Format 'yyyy-MM-dd')`"}"
$batch = (Invoke-Json "POST" "$base/batches" $adminJar $adminToken $batchJson) | ConvertFrom-Json
Write-Host "PASS 创建批次 id=$($batch.id) code=$($batch.batchCode)"

$uploadA = Upload-Image $pngA $batch.id
$taskId = [long]$uploadA.taskId
$imageId = [long]$uploadA.imageId
Write-Host "PASS 上传主流程图片 taskId=$taskId"

$users = (& curl.exe -s -b $adminJar "$base/users?role=INSPECTOR&keyword=inspector01&page=0&size=10") | ConvertFrom-Json
$inspectorId = [long](($users.content | Where-Object { $_.username -eq "inspector01" } | Select-Object -First 1).id)
if (-not $inspectorId) { throw "未找到 inspector01" }

# ---------- 2. 分配 → 开始 → 标注 → 提交 ----------
$assigned = Assign-Task $taskId $inspectorId 0
if ($assigned.status -ne "ASSIGNED") { throw "分配失败" }
Write-Host "PASS 分配任务 version=$($assigned.version)"

$inspToken = Login $inspJar "inspector01" $inspectorPassword
$startJson = "{`"version`":$($assigned.version),`"comment`":`"开始标注`"}"
$started = (Invoke-Json "POST" "$base/tasks/$taskId/start" $inspJar $inspToken $startJson) | ConvertFrom-Json
if ($started.status -ne "IN_PROGRESS") { throw "开始任务失败" }
$annJson = '{"annotations":[{"categoryId":1,"x":0.12,"y":0.16,"w":0.30,"h":0.27,"imgWidth":800,"imgHeight":600,"note":"E2E 划痕"}]}'
$ann = (Invoke-Json "PUT" "$base/tasks/$taskId/annotations" $inspJar $inspToken $annJson) | ConvertFrom-Json
if ($ann.Count -ne 1) { throw "标注保存失败" }
$submitJson = "{`"version`":$($started.version),`"comment`":`"首次提交`"}"
$submitted = (Invoke-Json "POST" "$base/tasks/$taskId/submit" $inspJar $inspToken $submitJson) | ConvertFrom-Json
if ($submitted.status -ne "SUBMITTED") { throw "提交审核失败" }
Write-Host "PASS 标注并提交 version=$($submitted.version)"

# ---------- 3. 审核员驳回 → 质检员修改 → 再提交 → 通过 ----------
$rev1Token = Login $rev1Jar "reviewer01" $reviewerPassword
# 审核工作台/质检任务页都有批次筛选，三角色读取必须可用
$batchForReviewer = (& curl.exe -s -b $rev1Jar "$base/batches?page=0&size=10") | ConvertFrom-Json
if (-not ($batchForReviewer.content | Where-Object { $_.id -eq $batch.id })) { throw "审核员读取批次筛选失败" }
$batchForInspector = (& curl.exe -s -b $inspJar "$base/batches?page=0&size=10") | ConvertFrom-Json
if (-not ($batchForInspector.content | Where-Object { $_.id -eq $batch.id })) { throw "质检员读取批次筛选失败" }
Write-Host "PASS 质检员/审核员批次筛选接口可用"
$pending = (& curl.exe -s -b $rev1Jar "$base/tasks?status=SUBMITTED&page=0&size=50") | ConvertFrom-Json
if (-not ($pending.content | Where-Object { $_.id -eq $taskId })) { throw "待审核列表缺少任务 $taskId" }
$pendingByBatch = (& curl.exe -s -b $rev1Jar "$base/tasks?status=SUBMITTED&batchId=$($batch.id)&page=0&size=50") | ConvertFrom-Json
if (-not ($pendingByBatch.content | Where-Object { $_.id -eq $taskId })) { throw "批次筛选缺少任务 $taskId" }
Write-Host "PASS 待审核列表与批次筛选可见 taskId=$taskId"

$rejectJson = "{`"version`":$($submitted.version),`"comment`":`"框选边界不清，请重新标注`"}"
$rejected = (Invoke-Json "POST" "$base/tasks/$taskId/reject" $rev1Jar $rev1Token $rejectJson) | ConvertFrom-Json
if ($rejected.status -ne "REJECTED") { throw "驳回归失败" }
Write-Host "PASS 驳回 status=REJECTED version=$($rejected.version)"

$resumeJson = "{`"version`":$($rejected.version),`"comment`":`"按意见修改`"}"
$resumed = (Invoke-Json "POST" "$base/tasks/$taskId/start" $inspJar $inspToken $resumeJson) | ConvertFrom-Json
if ($resumed.status -ne "IN_PROGRESS") { throw "驳回后重新开始失败" }
$annJson2 = '{"annotations":[{"categoryId":1,"x":0.13,"y":0.17,"w":0.29,"h":0.26,"imgWidth":800,"imgHeight":600,"note":"E2E 已按意见调整"}]}'
$ann2 = (Invoke-Json "PUT" "$base/tasks/$taskId/annotations" $inspJar $inspToken $annJson2) | ConvertFrom-Json
if ([math]::Abs($ann2[0].xNorm - 0.13) -gt 0.000001) { throw "修改后坐标不一致" }
$resubmitJson = "{`"version`":$($resumed.version),`"comment`":`"已修改后重新提交`"}"
$resubmitted = (Invoke-Json "POST" "$base/tasks/$taskId/submit" $inspJar $inspToken $resubmitJson) | ConvertFrom-Json
if ($resubmitted.status -ne "SUBMITTED") { throw "重新提交失败" }
Write-Host "PASS 驳回后修改并重新提交 version=$($resubmitted.version)"

$approveJson = "{`"version`":$($resubmitted.version),`"comment`":`"标注符合要求`"}"
$approved = (Invoke-Json "POST" "$base/tasks/$taskId/approve" $rev1Jar $rev1Token $approveJson) | ConvertFrom-Json
if ($approved.status -ne "APPROVED") { throw "审核通过失败" }
Write-Host "PASS 审核通过 status=APPROVED version=$($approved.version)"

$history = (& curl.exe -s -b $rev1Jar "$base/tasks/$taskId/history") | ConvertFrom-Json
$actions = ($history | ForEach-Object { $_.action }) -join ","
if ($history.Count -ne 7) { throw "审核历史数量错误: $actions" }
if ($actions -ne "ASSIGN,START,SUBMIT,REJECT,START,SUBMIT,APPROVE") { throw "审核历史顺序错误: $actions" }
Write-Host "PASS 审核历史完整 $actions"

# ---------- 4. 权限与状态绕过 ----------
$inspApprove = Invoke-Status "POST" "$base/tasks/$taskId/approve" $inspJar $inspToken "{`"version`":$($approved.version)}"
if ($inspApprove.code -ne 403) { throw "质检员审批未被拒绝: $($inspApprove.code)" }
$revAnn = Invoke-Status "PUT" "$base/tasks/$taskId/annotations" $rev1Jar $rev1Token '{"annotations":[]}'
if ($revAnn.code -ne 403) { throw "审核员改标注未被拒绝: $($revAnn.code)" }
$adminApprove = Invoke-Status "POST" "$base/tasks/$taskId/approve" $adminJar $adminToken "{`"version`":$($approved.version)}"
if ($adminApprove.code -ne 403) { throw "管理员审批未被拒绝: $($adminApprove.code)" }
$lateSubmit = Invoke-Status "POST" "$base/tasks/$taskId/submit" $inspJar $inspToken "{`"version`":$($approved.version)}"
if ($lateSubmit.code -ne 409) { throw "已通过任务仍可提交: $($lateSubmit.code)" }
$lateAnn = Invoke-Status "PUT" "$base/tasks/$taskId/annotations" $inspJar $inspToken '{"annotations":[]}'
if ($lateAnn.code -ne 409) { throw "已通过任务仍可改标注: $($lateAnn.code)" }
Write-Host "PASS 权限矩阵与已通过状态保护"

# ---------- 5. 双审核员并发 ----------
$uploadB = Upload-Image $pngB $batch.id
$taskB = [long]$uploadB.taskId
$assignedB = Assign-Task $taskB $inspectorId 0
$startB = (Invoke-Json "POST" "$base/tasks/$taskB/start" $inspJar $inspToken `
    "{`"version`":$($assignedB.version),`"comment`":`"并发准备`"}" ) | ConvertFrom-Json
Invoke-Json "PUT" "$base/tasks/$taskB/annotations" $inspJar $inspToken $annJson | Out-Null
$submitB = (Invoke-Json "POST" "$base/tasks/$taskB/submit" $inspJar $inspToken `
    "{`"version`":$($startB.version),`"comment`":`"并发提交`"}" ) | ConvertFrom-Json

$rev2Token = Login $rev2Jar "reviewer02" $reviewerPassword
$out1 = Join-Path $env:TEMP "vqc-p3-concurrent-1.txt"
$out2 = Join-Path $env:TEMP "vqc-p3-concurrent-2.txt"
$body = "{`"version`":$($submitB.version),`"comment`":`"并发审批`"}"
$p1 = Start-CurlProcess $rev1Jar $rev1Token $out1 "$base/tasks/$taskB/approve" $body
$p2 = Start-CurlProcess $rev2Jar $rev2Token $out2 "$base/tasks/$taskB/approve" $body
$p1.WaitForExit(30000) | Out-Null
$p2.WaitForExit(30000) | Out-Null
# curl 的 -w 状态码走 stdout；响应体已由 -o 写入 outFile
$code1 = [int]$p1.StandardOutput.ReadToEnd().Trim()
$code2 = [int]$p2.StandardOutput.ReadToEnd().Trim()
if (-not ((($code1 -eq 200) -and ($code2 -eq 409)) -or (($code1 -eq 409) -and ($code2 -eq 200)))) {
    throw "并发审批结果不符合预期: $code1 / $code2"
}
$taskBAfter = (& curl.exe -s -b $rev1Jar "$base/tasks/$taskB") | ConvertFrom-Json
if ($taskBAfter.status -ne "APPROVED") { throw "并发后最终状态不是 APPROVED: $($taskBAfter.status)" }
$histB = (& curl.exe -s -b $rev1Jar "$base/tasks/$taskB/history") | ConvertFrom-Json
$approveCount = ($histB | Where-Object { $_.action -eq "APPROVE" }).Count
if ($approveCount -ne 1) { throw "并发审批写入 $approveCount 条 APPROVE 记录" }
Write-Host "PASS 双审核员并发审批 200/409，最终 APPROVED，APPROVE 记录=1"

Write-Host "E2E3_RESULT=PASS taskId=$taskId concurrencyTaskId=$taskB history=$actions"
