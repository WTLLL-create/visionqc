# Phase 2 学习与实现说明（图片上传 + 矩形缺陷标注）

> 这份文档回答三个问题：**这一阶段解决了什么业务问题**、**代码为什么这样写**、**你可以怎样自己验证和继续改**。面向“会写 CRUD、但没做过多图上传与画布标注”的读者。

---

## 1. 业务问题与范围

工业质检现场的实际流程是：

1. 产线按批次拍照，一个批次对应一台设备、一段时间、一种产品型号；
2. 质检员从批次里挑图片，把缺陷位置框出来并标类别（划痕/凹陷/裂纹……）；
3. （Phase 3）审核员复核标注是否合格，驳回则质检员重标；
4. （Phase 4）按类别/时间做质量统计。

Phase 2 只做**第 1、2 步的闭环**：

- 管理员：建设备 → 建批次 → 上传一张或多张 JPG/PNG；
- 质检员：打开任务 → 在图上画矩形 → 选类别/填备注 → 保存 → 刷新后仍在；
- 不提前做审核流、质量看板、AI 模型（任务书明确要求）。

---

## 2. 关键设计决策（以及为什么）

### 2.1 文件存磁盘、元数据存数据库

图片二进制不进 MySQL（会让库迅速膨胀、备份变慢、连接被大对象拖住）。数据库只存：

- `file_key`：服务端生成的 `{uuid}.{ext}`，**永远不用客户端文件名做路径**；
- `sha256`、宽高、字节数、MIME、状态、批次 ID。

存储目录（Docker 中由卷 `vqc-storage` 挂载到 `/data/vqc-storage`）：

```
/data/vqc-storage/
├── staging/                 # 上传中的临时文件，1 小时未完成会被清理
└── final/{batchId}/{uuid}.png
```

### 2.2 上传为什么先写 staging，再原子移动

如果直接写最终目录，可能出现“文件写了一半、数据库已提交”的不一致。正确顺序是：

1. 写 staging（此时 DB 无记录）；
2. ImageIO 识别真实格式、读宽高（不信任 `Content-Type` 与扩展名）；
3. 计算 SHA-256，同批次内去重（幂等）；
4. DB 插入 `STAGED`；
5. `Files.move(..., ATOMIC_MOVE)` 移到 final；
6. DB 改为 `ACTIVE`，并创建 `inspection_task`。

任一步失败：删 staging、把记录标 `FAILED`。定时任务再兜底清理“超时 STAGED”和“失败超过 24h”的记录，避免垃圾文件永久堆积。

### 2.3 为什么用 SHA-256 批内去重

同一批次里重复上传同一个文件，不应该产生两条记录、两份磁盘占用。唯一约束 `uk_batch_sha(batch_id, sha256)` 保证并发下也不会重复；Service 先查再插，命中就返回 `duplicate=true` 和已有 `imageId`。

选择“批内去重”而不是全局去重：不同批次重复出现同一张图本身是业务事实（可能是复检/补拍），跨批次合并会破坏批次归属。

### 2.4 为什么坐标要归一化

画布会缩放、平移、窗口大小会变。**不能存屏幕像素**，否则刷新或换分辨率后框会漂移。

做法：矩形最终存“相对原图的 0~1 比例”：

```
xNorm = x / imageWidth
yNorm = y / imageHeight
wNorm = w / imageWidth
hNorm = h / imageHeight
```

数据库用 `DECIMAL(8,6)`（保留 6 位小数），后端再按 `HALF_UP` 截到 6 位。读取时前端用 `xNorm * imageWidth` 还原像素位置。

这样同一份标注在 1366×768 笔记本和 2K 显示器上都能落在同一块缺陷上。

### 2.5 为什么选 Fabric.js 7

- 需求是“矩形框 + 选中/拖动/缩放/删除”，Fabric 自带对象模型、选择框、控制点、序列化，比手写 canvas 命中检测省很多；
- 底图作为**普通对象**加入画布并 `sendObjectToBack`，而不是 `backgroundImage`：这样缩放/平移统一走 `viewportTransform`，导出和坐标换算只有一套逻辑；
- Fabric 7 的事件对象直接提供 `scenePoint`（图像坐标系，不含视口变换），比手算 `clientX - rect.left - translateX` 更可靠。项目里 `scenePointFromOpt()` 优先用它，手算只作为兜底。

### 2.6 为什么标注保存是“全量替换”

一次编辑可能新增、移动、删除多个框。逐条 diff 需要前端传 id、处理并发删除，复杂且容易漏。Phase 2 采用：

```
PUT /tasks/{id}/annotations  { annotations: [ ... ] }
```

服务端在一个事务里 `deleteByTaskId + flush` 再批量插入，语义简单：**提交的列表就是最终状态**。代价是标注数量大时 SQL 偏多，但单图标注通常 < 50 条，可接受。

### 2.7 安全：为什么 Session 也要 CSRF

Session Cookie 会被浏览器自动带上，恶意站点可以诱导已登录用户发请求。启用 Spring Security 的 `CookieCsrfTokenRepository`：

1. 前端启动时 `GET /api/v1/auth/csrf`，后端下发 `XSRF-TOKEN` Cookie 和 token；
2. axios 请求拦截器把 token 放到 `X-XSRF-TOKEN` 头（非 GET 请求）；
3. 后端校验头与 Cookie 一致，否则 403。

同时 `/api/v1/images/**` 的读接口要求登录，上传/删除要求 ADMIN；`/tasks/**` 要求 INSPECTOR/ADMIN。**前端隐藏按钮只是体验，真正的权限在后端。**

---

## 3. 关键文件地图

### 后端

| 文件 | 作用 |
|------|------|
| `storage/FileStorageService.java` | staging/final 目录、格式识别、读尺寸、SHA-256、原子移动、路径穿越防护 |
| `storage/StorageCleanupJob.java` | 每小时清理超时 STAGED / 过期 FAILED |
| `image/ImageService.java` | 上传编排（校验→暂存→识别→去重→落库→移动→建任务） |
| `image/ImageTransactionalOps.java` | `REQUIRES_NEW` 小事务，保证失败状态能独立提交 |
| `image/ImageController.java` | 上传/分页/详情/文件流/逻辑删除 |
| `image/ImageAsset.java` | 图片元数据实体（STAGED/ACTIVE/FAILED/DELETED） |
| `task/TaskService.java` | 上传后自动建任务、任务查询、可编辑状态判断 |
| `annotation/AnnotationService.java` | 全量替换、类别校验、越界校验、坐标截断 |
| `annotation/AnnotationController.java` | GET/PUT `/api/v1/tasks/{taskId}/annotations` |
| `config/SecurityConfig.java` | 权限矩阵、CSRF、Session |
| `auth/CsrfController.java` | 下发 CSRF token |

### 前端

| 文件 | 作用 |
|------|------|
| `src/api/request.js` | axios 实例、CSRF 注入、401/403/409/413 统一提示 |
| `src/api/image.js` / `task.js` | 图片与任务/标注 API |
| `src/views/ImageView.vue` | 批次选择、多图上传、分页列表、预览、删除 |
| `src/views/MyTaskListView.vue` | 我的任务列表、状态过滤、进入标注 |
| `src/views/AnnotateView.vue` | Fabric 画布：缩放/平移/画框/选中/编辑/删除/保存/恢复 |

---

## 4. 一次上传的调用链

```
ImageView.vue
  └─ uploadImages(batchId, FormData)
       └─ POST /api/v1/images/upload/{batchId}   （带 X-XSRF-TOKEN）
            └─ ImageController.upload
                 └─ ImageService.upload
                      ├─ 校验批次存在且未 CLOSED
                      └─ 对每个文件 storeOne：
                           1. 空文件/超 10MB → fail（不落盘）
                           2. saveToStaging
                           3. detectImageFormat（ImageReader，识别 jpg/png）
                           4. readImageSize（ImageIO）
                           5. sha256 → 查同批次是否已存在
                           6. ops.saveStaged（REQUIRES_NEW 提交 STAGED）
                           7. promoteToFinal（ATOMIC_MOVE）
                           8. ops.markActive
                           9. taskRepository.save（image_id 唯一）
```

响应是**逐文件结果数组**，一个文件失败不影响其他文件：

```json
[{"imageId":1,"taskId":1,"duplicate":false,"success":true,"message":null}]
```

---

## 5. 一次标注保存/恢复的调用链

```
AnnotateView.vue
  ├─ 加载：GET /tasks/{id} + GET /tasks/{id}/annotations + GET /defect-categories
  ├─ 画框：mousedown 记录 scenePoint → mousemove 改 Rect → mouseup 结束
  │        （坐标 clamp 到原图边界；结束后 selectable=true）
  ├─ 保存：遍历矩形 → 用原图宽高归一化 round(6) → 前端预校验 x+w<=1
  │        → PUT /tasks/{id}/annotations（全量）
  └─ 刷新：GET 标注 → xNorm * imageWidth 还原 Rect → 重新 add 到画布
```

服务端二次校验（不信任前端）：

- 每个坐标必须在 `[0,1)`，宽高 `>0`；
- `x+w <= 1.000001`、`y+h <= 1.000001`（容忍前端 6 位小数舍入）；
- 类别必须存在且 `enabled=1`；
- 任务状态必须是 `NEW/ASSIGNED/IN_PROGRESS/REJECTED`，否则 409。

### 坐标换算示例（800×600 原图）

画布上框在 (100, 80)，大小 240×160：

```
xNorm = 100/800 = 0.125
yNorm = 80/600  = 0.133333
wNorm = 240/800 = 0.3
hNorm = 160/600 = 0.266667
```

存库后读回：`0.125 * 800 = 100`，即使画布当前缩放 0.4 也不影响。

---

## 6. 权限矩阵（Phase 2 实际生效）

| 接口 | ADMIN | INSPECTOR | REVIEWER | 未登录 |
|------|-------|-----------|----------|--------|
| `/devices`、`/users`、`/batches` 写 | ✅ | ❌ | ❌ | ❌ |
| `POST /images/upload/{batchId}` | ✅ | ❌ | ❌ | ❌ |
| `DELETE /images/{id}` | ✅ | ❌ | ❌ | ❌ |
| `GET /images`、`/images/{id}/file` | ✅ | ✅ | ✅ | ❌ |
| `GET /defect-categories` | ✅ | ✅ | ✅ | ❌ |
| `/tasks/**`（含标注） | ✅ | ✅ | ❌ | ❌ |

> `admin/change-me-admin` 仅用于本地演示。`SeedDataService` 由 `APP_SEED_ADMIN` 控制，生产部署必须设为 `false`；README 已注明不要暴露到公网。

---

## 7. 测试与验收（可复现）

### 7.1 后端自动化测试

```powershell
cd backend
mvn test
```

当前结果（2026-11-17，含 Phase 2.5/3 最终态）：

| 测试类 | 用例数 | 结果 |
|--------|--------|------|
| `ImageUploadIntegrationTest` | 9 | 全部通过 |
| `AnnotationIntegrationTest` | 7 | 全部通过 |
| `ReviewWorkflowIntegrationTest` | 3 | 全部通过 |
| `TaskConcurrencyTest` | 2 | 全部通过 |
| `TaskStatusTest` | 4 | 全部通过 |
| `VisionqcApplicationTests` | 1 | 通过 |
| 合计 | **26** | **0 失败** |

集成测试使用 Docker MySQL 的独立库 `vqc_test`（`application-test.yml`），不会清空开发库 `vqc`；文件写 `%TEMP%\vqc-test-storage`，不污染 Docker 存储卷。

覆盖点：

- 正常上传 PNG → ACTIVE、宽高正确、自动建任务、文件可读；
- 同批次重复文件 → `duplicate=true`，库中仅 1 条；
- 文本伪装 PNG → 拒绝且不落库；
- INSPECTOR 调上传 → 403；
- 未登录读图片 → 403；
- CLOSED 批次上传 → 4xx；
- 标注保存后读取：`xNorm=0.123456` 精确一致；
- 像素换算：`0.25*800=200` 等；
- 全量替换：提交空数组后旧标注被删除；
- 越界 `x=0.6,w=0.5` → 400；
- 类别不存在 → 400；
- 任务 `SUBMITTED` → PUT 409；
- REVIEWER GET 标注 200（只读查看缺陷框）；REVIEWER PUT 标注 403。

### 7.2 真实 E2E（Docker + HTTP，无 Mock）

脚本：`scripts/e2e-phase2.ps1`

```powershell
cd <repo-root>
pwsh -NoProfile -File .\scripts\e2e-phase2.ps1
```

实测输出（2026-11-17）：

```
PASS 登录 admin
PASS 创建设备 id=2
PASS 创建批次 id=1 code=B-E2E-202443
PASS 上传图片 imageId=1 taskId=1
PASS 登录 inspector01
PASS 标注保存 xNorm=0.125 wNorm=0.3
PASS 标注回读一致 note=E2E 划痕
INFO 重启 MySQL 与后端容器 ...
PASS 重启后图片可读且 SHA-256 一致 8714d53c...
PASS 重启后标注仍在 xNorm=0.125
PASS 重复上传幂等 duplicate=True imageId=1
E2E_RESULT=PASS
```

这条链路验证了：真实 Session + CSRF、真实 multipart 上传、真实文件落盘、真实 MySQL 持久化、**Docker 卷在容器重启后不丢数据**。

---

## 8. 踩过的坑与正误对比

### 8.1 Fabric 7 与 v6 的坐标 API

错误：

```javascript
// 直接拿鼠标事件手算，忽略视口矩阵与 canvas 偏移
const x = e.clientX - canvasEl.offsetLeft
```

正确（本项目）：

```javascript
if (opt?.scenePoint) return { x: opt.scenePoint.x, y: opt.scenePoint.y }
// 兜底才用 clientX + viewportTransform 手算
```

### 8.2 JPA `validate` 与 CHAR 列

错误：

```java
@Column(length = 64) private String sha256;
```

MySQL 里是 `CHAR(64)`，Hibernate 6 `validate` 认成 `VARCHAR(64)` → 启动失败 `wrong column type`。

正确：

```java
@Column(length = 64, columnDefinition = "char(64)")
```

### 8.3 CSRF 开启后脚本写请求 403

错误：只带 `JSESSIONID` 直接 POST。
正确：先 `GET /api/v1/auth/csrf`，后续写请求带 `X-XSRF-TOKEN` 头，且保持同一 Cookie 会话。

### 8.4 Docker 数据盘 I/O error

本次遇到 `docker_data.vhdx` 损坏，所有镜像拉取报 `input/output error`。处理是**先备份整块 vhdx**（含用户旧项目卷数据），再重建，而不是直接删。教训：卷数据要有独立备份策略，`docker compose down -v` 永远不要随手执行。

---

## 9. 你可以自己做的练习

1. 把单文件上限从 10MB 改成 5MB，同时改 Spring multipart 配置与 `app.max-image-bytes`，验证 413 文案；
2. 给 `Annotation` 增加 `severity`（轻微/严重）字段：写 Flyway V3、改 DTO/Service、前端加下拉，跑一遍集成测试；
3. 把标注保存改成“带 id 的增量更新”，思考并发删除时如何处理（提示：`@Version` 或 ETag）；
4. 用 `docker compose stop backend && docker compose start backend`，验证 Session 失效后前端 401 跳转是否正常；
5. 故意上传 0 字节文件、损坏的 PNG、20000×20000 的超大图，记录每种失败路径的状态码与提示。

---

## 10. 验收清单（对应任务书要求）

- [x] 单图/多图上传，JPG/PNG 真实格式校验
- [x] 10MB 大小限制与 413 处理
- [x] SHA-256 计算与批内去重幂等
- [x] 宽高读取并落库
- [x] 批次关联、分页查询
- [x] 安全预览（登录可读、服务端解析路径、私有缓存）
- [x] 文件-DB 一致性（STAGED→ACTIVE/FAILED + 定时清理）
- [x] Docker 持久化卷（重启后文件与标注仍在）
- [x] Fabric 矩形标注：缩放/平移/画框/选中/编辑/删除/类别/备注/保存/刷新恢复
- [x] 归一化坐标存储与回读
- [x] 后端集成测试 13 条 + 单元 5 条
- [x] 真实 E2E（管理员建批次→上传→质检员标注→重启→恢复）
- [x] 本地跑通：前端 `http://localhost/` 200、Nginx `/api` 反代 CSRF 200、匿名访问 `/auth/me` 403
- [x] 最小安全修复：后端角色校验、CSRF、admin 仅演示
- [ ] 浏览器人工演示（Phase 2.5 新指令要求真实浏览器验收；AI 环境 bu CDP 断开、cu 曾被锁定，用户明确“展示我来做”，故仍未验收；人工步骤见 `docs/08-Phase3审核工作流与并发验收.md` 第 9 节）
- [x] 审核工作流（Phase 3 已完成，见 `docs/03-状态机与审核流程.md`、`docs/08-Phase3审核工作流与并发验收.md`）
- [ ] 质量看板 / AI 模型（按任务书要求留到后续阶段）

---

## 11. Phase 2.5 图片安全修补补充（2026-11-17）

Phase 2 完成后，按新指令补充了图片解码安全边界：

| 修补 | 实现 | 测试 |
|------|------|------|
| 最大解码像素 | `app.max-image-pixels` 默认 2500 万；先读头部宽高，超限拒绝，不整图解码 | 测试上限 50 万；1000×600 被拒 |
| JPG EXIF 方向 | 手工解析 APP1/TIFF Orientation；orientation≠1 明确拒绝，避免浏览器旋转导致坐标错位 | EXIF=6 的 JPEG 被拒 |
| 读尺寸与解码一致 | `readImageSize` 只读头部；`decodeMatches` 完整解码校验尺寸 | 正常 JPEG 宽高正确 |
| 解码失败清理 | 所有失败分支 `deleteQuietly(staging)` | staging 无残留 |

`ImageUploadIntegrationTest` 由 6 条扩到 9 条，全部通过。当前后端全量测试为 26 条（含 Phase 3 的 3 条审核闭环 + 2 条并发），全部通过。

> 浏览器画布交互仍未由 AI 验收；上方清单中的“浏览器人工演示”保持未勾选，避免把未执行的操作写成已验收。
