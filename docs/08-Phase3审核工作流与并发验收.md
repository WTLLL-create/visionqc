# Phase 3 审核工作流与并发验收

> 项目：VisionQC 工业视觉质检与缺陷复核平台
> 验收日期：2026-11-17
> 范围：Phase 2.5 图片安全修补与最终回归 + Phase 3 审核状态机、审核工作台、JPA 乐观锁并发
> 结论：**后端/前端构建/自动化测试/HTTP E2E 全部通过；真实浏览器画布交互由用户本人演示，AI 未代为验收，本文明确标注为“未验收”并给出人工步骤。**

---

## 0. 验收结论总览

| 验收项 | 结果 | 证据 |
|--------|------|------|
| Phase 2.5 图片解码像素上限 | 通过 | `ImageUploadIntegrationTest` 9/9，含超像素拒绝用例 |
| Phase 2.5 JPG EXIF 方向修补 | 通过 | EXIF orientation=6 的 JPEG 被明确拒绝（400） |
| Phase 2.5 无效图片清理 | 通过 | 各失败分支 `deleteQuietly`，测试校验 staging 无残留 |
| Phase 3 状态机（6 状态、6 条转换） | 通过 | `TaskStatusTest` 4/4 + `ReviewWorkflowIntegrationTest` 3/3 |
| Phase 3 权限矩阵 | 通过 | 集成测试 + E2E：质检员审批 403、审核员改标注 403、管理员审批 403 |
| Phase 3 审核历史同事务 | 通过 | `ReviewService.record` 使用 `Propagation.MANDATORY`；7 条历史顺序正确 |
| JPA 乐观锁真实并发 | 通过 | `TaskConcurrencyTest` 2/2：API 并发 200/409；两事务后提交者抛 `OptimisticLockingFailureException` |
| 后端全量测试 | 通过 | `mvn test`：26 tests，0 failures，0 errors |
| 前端构建 | 通过 | `npm run build` 成功（ReviewView/History 等 chunk 正常产出） |
| Phase 2 回归 E2E | 通过 | `scripts/e2e-phase2.ps1`：容器重启后 SHA-256 与标注仍在 |
| Phase 3 全流程 E2E | 通过 | `scripts/e2e-phase3.ps1`：`E2E3_RESULT=PASS taskId=8 concurrencyTaskId=9` |
| V3 数据库迁移 | 通过 | `vqc_test`、运行库 `vqc` 均为 Flyway v3 |
| Docker 重建（不清卷） | 通过 | 后端/前端镜像重建，`vqc_vqc-mysql-data`、`vqc_vqc-storage` 保留 |
| 真实浏览器画布验收 | **未验收** | AI 浏览器会话不可用；用户明确“展示我来做”，故不假称通过，人工步骤见第 9 节 |

---

## 1. Phase 2.5 图片安全修补

### 1.1 最大解码像素限制

- 配置：`app.max-image-pixels`，默认 `25_000_000`（约 25MP）；测试环境 `500_000`。
- 识别格式后先读取图片头部宽高，计算 `width * height`，超过上限直接拒绝，**不进行整图解码**，避免解码炸弹占满内存。
- 失败信息：`图片分辨率过高（WxH），最大允许 ... 像素`；HTTP 400。
- 失败分支统一 `deleteQuietly(stagingPath)`，防止 staging 残留。

### 1.2 JPG EXIF 方向修补

- Java `ImageIO` 解码 JPEG 不会自动应用 EXIF Orientation；浏览器却会旋转显示，导致前端画框坐标与后端原图尺寸错位。
- 实现 `FileStorageService.readExifOrientation(Path)`：手工解析 JPEG APP1/TIFF 的 Orientation 标签（1~8）；PNG 或无 EXIF 返回 1。
- 策略：**orientation != 1 明确拒绝上传**，提示“请先转为正向图片”。这是最安全的策略，避免“后端不旋转、前端旋转”的隐性错位。
- 解析器包含 `readMarker / readU16 / readU32 / parseTiffOrientation / skipFully`，对越界/截断输入返回 1 或安全失败，不抛出未捕获异常。

### 1.3 尺寸读取与显示一致性

- `readImageSize` 改为只读 `ImageReader` 头部宽高，不再整图解码。
- 新增 `decodeMatches(file, expectedW, expectedH)`：完整解码一次，校验实际尺寸与头部一致；不一致或解码失败则拒绝并清理 staging。
- 正常 JPEG 上传会记录真实宽高，前端标注使用同一尺寸。

### 1.4 Phase 2.5 测试结果

```
ImageUploadIntegrationTest: Tests run: 9, Failures: 0, Errors: 0
- 正常 PNG 上传 + 尺寸/去重
- 正常 JPEG 上传（尺寸读取一致）
- EXIF Orientation=6 的 JPEG 被拒绝
- 超过像素上限（1000x600 > 500000）被拒绝
- 非图片/损坏图片拒绝并清理 staging
- 10MB 限制、重复上传幂等、批内 SHA-256 去重等
```

### 1.5 真实浏览器画布验收：未验收

**未验收原因（如实记录）：**

- Aha 产品浏览器 `bu`：`list_tabs()` 返回空，`new_tab` 报 `CDP session is disconnected`，无法执行页面交互。
- Windows 桌面 `cu`：曾因 Windows 会话锁定返回 `CU_SESSION_LOCKED`；用户跳过接管。后续桌面解锁，Chrome 启动后停留在用户资料选择页；用户明确表示“展示我来做，告诉我路径就好”。
- 因此 AI **没有**执行登录、上传、多矩形拖动缩放、滚轮平移、保存刷新等真实浏览器操作，不能标记为“已验收”。

人工验收步骤见第 9 节，用户按步骤演示即可；演示后可将结果补写为“已验收”。

---

## 2. Phase 3 状态机设计

### 2.1 状态与转换

```
NEW ──assign(ADMIN)──► ASSIGNED ──start(INSPECTOR 本人)──► IN_PROGRESS
                                                              │
                                                       submit(INSPECTOR 本人，≥1 标注)
                                                              ▼
                                                          SUBMITTED
                                                          │        │
                                             approve(R)   │        │ reject(R，原因必填)
                                                          ▼        ▼
                                                      APPROVED   REJECTED
                                                       （终态）      │
                                                                    │ start(本人)
                                                                    ▼
                                                               IN_PROGRESS
                                                                    │
                                                                    └──► SUBMITTED
```

| 当前状态 | 允许的下一状态 | 触发动作 | 角色 |
|----------|----------------|----------|------|
| NEW | ASSIGNED | assign | ADMIN |
| ASSIGNED | IN_PROGRESS | start | INSPECTOR 本人 |
| IN_PROGRESS | SUBMITTED | submit | INSPECTOR 本人 |
| SUBMITTED | APPROVED | approve | REVIEWER（非本人任务） |
| SUBMITTED | REJECTED | reject | REVIEWER（非本人任务，原因必填） |
| REJECTED | IN_PROGRESS | start | INSPECTOR 本人 |
| APPROVED | 无 | — | — |

`TaskStatus.canTransitionTo` 是唯一转换表；Service 的 `assertTransition` 在每次动作前调用。非法转换统一 409，不依赖前端按钮隐藏。

### 2.2 版本流（正常闭环）

```
上传 task v0
assign  → v1  ASSIGN 记录
start   → v2  START 记录
submit  → v3  SUBMIT 记录
reject  → v4  REJECT 记录
start   → v5  START 记录
submit  → v6  SUBMIT 记录
approve → v7  APPROVE 记录
```

`review_records.version` 记录的是本次动作完成后的任务版本，与 `inspection_tasks.version` 对齐。

---

## 3. 权限矩阵

| 操作 | 接口 | ADMIN | INSPECTOR | REVIEWER | 服务层额外校验 |
|------|------|:-----:|:---------:|:--------:|----------------|
| 任务列表/详情 | GET `/tasks`、`/tasks/{id}` | ✅ 全量 | ✅ 仅本人 | ✅ 全量 | 质检员 `inspectorId` 必须等于当前 userId |
| 审核历史 | GET `/tasks/{id}/history` | ✅ | ✅ 仅本人任务 | ✅ | `assertCanView` |
| 分配任务 | POST `/tasks/{id}/assign` | ✅ | ❌ | ❌ | 目标用户存在、启用、含 INSPECTOR 角色 |
| 开始/继续 | POST `/tasks/{id}/start` | ❌ | ✅ 本人 | ❌ | `assertInspectorOwns` |
| 提交审核 | POST `/tasks/{id}/submit` | ❌ | ✅ 本人 | ❌ | 归属人 + 至少 1 条标注 |
| 审批通过/驳回 | POST `/tasks/{id}/approve|reject` | ❌ | ❌ | ✅ | 禁止审核自己标注的任务 |
| 读取标注 | GET `/tasks/{id}/annotations` | ✅ | ✅ 本人任务 | ✅ 只读 | `assertCanView` |
| 保存标注 | PUT `/tasks/{id}/annotations` | ❌ | ✅ 本人 | ❌ | `isEditable` 状态白名单 |

安全分层：

1. `SecurityConfig` 按 **HTTP 方法 + 动作路径**授权，先挡角色不匹配（401/403）。
2. `Actor.from(Authentication)` 从真实 `AppUserDetails` 提取 userId，不信任前端传参。
3. Service 再做归属/状态/版本校验，防止同角色越权（例如质检员 A 改质检员 B 的任务）。
4. 前端按钮隐藏只是体验优化，不是安全边界。

---

## 4. 后端 Service 调用链

### 4.1 审批通过

```
POST /api/v1/tasks/{id}/approve
  TaskController.approve(id, TaskActionRequest, Authentication)
    → Actor.from(auth)                         // userId + roles
    → TaskService.approve(id, req, actor)
        → actor.requireRole("REVIEWER")        // 角色兜底
        → getEntity(id)                        // 任务不存在 → 404
        → checkVersion(task, req.version)      // 客户端版本 != DB 版本 → 409
        → assertTransition(task, APPROVED)     // SUBMITTED → APPROVED
        → assertNotSelfReview(task, actor)     // inspectorId != 当前 userId
        → task.setStatus(APPROVED); setReviewerId(actor.requireUserId())
        → taskRepository.saveAndFlush(task)    // @Version 在此生成 UPDATE ... WHERE version=old
        → reviewService.record(task, actor, "APPROVE", SUBMITTED, APPROVED, comment)
             → @Transactional(MANDATORY)       // 必须复用当前事务
             → INSERT review_records (... version = task.getVersion())
    → 返回 TaskResponse（含新 version）
```

### 4.2 驳回 / 提交 / 分配

- `reject`：`REVIEWER` → 版本/状态/自审校验 → `comment` 非空校验 → 状态 REJECTED + `REJECT` 记录。
- `submit`：`INSPECTOR` 本人 → 版本/状态校验 → `annotationRepository.countByTaskId(id) >= 1` → SUBMITTED + `SUBMIT` 记录。
- `assign`：`ADMIN` → 版本/状态校验 → 目标用户存在且启用且有 INSPECTOR 角色 → 写 `inspectorId` + ASSIGNED + `ASSIGN` 记录。
- `start`：`INSPECTOR` 本人 → 版本校验 → ASSIGNED 或 REJECTED → IN_PROGRESS + `START` 记录。

### 4.3 为什么审核记录用 `MANDATORY`

`ReviewService.record` 标注 `@Transactional(propagation = Propagation.MANDATORY)`：

- 如果调用方没有事务，直接抛出 `IllegalTransactionStateException`，从机制上阻止“状态先提交、历史另起事务失败”的不一致。
- 如果状态更新或历史写入任一失败，同一事务整体回滚：**不会出现任务已 APPROVED 但历史缺失，或历史写了但状态没变**。
- 相比在 `record()` 上写 `REQUIRES_NEW`，MANDATORY 保证原子性；相比 `REQUIRED`，MANDATORY 还会在误用（无事务调用）时立即暴露问题。

### 4.4 事务边界

| 操作 | 传播级别 | 原子内容 |
|------|----------|----------|
| assign / start / submit / approve / reject | `@Transactional`（REQUIRED） | 任务状态 + `review_records` 同一事务 |
| `ReviewService.record` | `MANDATORY` | 只允许加入已有事务 |
| 标注保存 | `@Transactional` | 全量删除旧标注 + 插入新标注 |
| 图片上传 | 编排式 + 建任务 `REQUIRES_NEW` | 文件 staging → DB 记录 → 原子 move；失败清理 |

---

## 5. JPA 乐观锁实现

### 5.1 数据库与实体

- `inspection_tasks.version INT NOT NULL DEFAULT 0`，`@Version private int version;`
- Hibernate 在 `UPDATE` 时自动追加 `WHERE version = ?`，成功后 `version + 1`；影响行数为 0 抛 `OptimisticLockingFailureException`。
- 审核记录表 V3 增加 `version INT NOT NULL DEFAULT 0`，用于把历史与任务版本关联。

### 5.2 双层校验

1. **显式校验**：`checkVersion(task, req.version)` 在业务动作前比较客户端提交的版本与数据库当前版本，快速返回可读的 409 文案。
2. **JPA 兜底**：即使两个请求同时读到相同版本并通过显式校验，数据库更新时仍只有一个能成功；另一个触发乐观锁异常，`GlobalExceptionHandler` 统一转为 409。

显式校验负责“用户体验 + 快速失败”，`@Version` 负责“真实并发下的最终正确性”，两者缺一不可。

### 5.3 并发测试方法与结果

#### 测试 A：双审核员 HTTP 并发审批（真实 MySQL）

`TaskConcurrencyTest.两个审核员同时审批仅一人成功且只写一条审核记录`

- 准备一个 SUBMITTED 任务，版本 `v`。
- 两个线程用 `CountDownLatch` 同时发起 `POST /approve`，携带同一 `version`，分别使用 `reviewer1`、`reviewer2` 的 `AppUserDetails`。
- 断言：
  - 两个 HTTP 状态码精确为 `200` 与 `409`；
  - 最终任务状态 `APPROVED`；
  - `review_records` 中 `APPROVE` 记录恰好 1 条（没有双写/覆盖历史）。
- 结果：通过。

#### 测试 B：两个 JPA 事务真实版本冲突

`TaskConcurrencyTest.JPA乐观锁_后提交事务因版本过期回滚`

- 线程 1 在事务中读取任务 → 通知线程 2 → 等待线程 2 提交。
- 线程 2 读取同一版本，改为 REJECTED 并提交。
- 线程 1 再把状态改为 APPROVED 并 `saveAndFlush`。
- 断言：线程 1 抛出 `OptimisticLockingFailureException`，最终状态为线程 2 的 REJECTED。
- 结果：通过。这证明冲突来自数据库版本列与 Hibernate 更新语义，而不是异常处理器“假装返回 409”。

#### 并发测试结果

```
TaskConcurrencyTest: Tests run: 2, Failures: 0, Errors: 0
```

日志中可见：

```
WARN GlobalExceptionHandler: Optimistic lock conflict:
Row was updated or deleted by another transaction ... [InspectionTask#1]
```

#### E2E 并发段

`scripts/e2e-phase3.ps1` 用两个独立 Session（reviewer01、reviewer02）同时审批同一 SUBMITTED 任务：

```
PASS 双审核员并发审批 200/409，最终 APPROVED，APPROVE 记录=1
```

---

## 6. 前端审核工作台

### 6.1 页面与路由

| 路由 | 组件 | 角色 | 功能 |
|------|------|------|------|
| `/review` | `ReviewView.vue` | REVIEWER / ADMIN | 待审核列表、状态/批次筛选、30s 自动刷新、只读图片+缺陷框、通过/驳回、历史时间线 |
| `/review-history` | `ReviewHistoryView.vue` | REVIEWER / ADMIN | 已通过/已驳回历史列表、时间线抽屉 |
| `/my-tasks` | `MyTaskListView.vue` | INSPECTOR / ADMIN | 质检员任务列表；管理员分配质检员 |
| `/annotate/:taskId` | `AnnotateView.vue` | INSPECTOR | Fabric 标注、开始/继续、提交审核、提交后只读 |

### 6.2 ReviewView 关键交互

- 默认筛选 `status=SUBMITTED`，可按批次过滤；表格点击行打开右侧抽屉。
- 抽屉内：
  - 原图只读展示，按归一化坐标叠加红色缺陷框（`xNorm/yNorm/wNorm/hNorm` → 百分比定位），不引入第二套坐标换算。
  - 标注列表展示类别、坐标、备注。
  - `el-timeline` 展示 `ASSIGN → START → SUBMIT → REJECT → START → SUBMIT → APPROVE`，含操作者、状态变化、评论、版本。
  - `REVIEWER && SUBMITTED` 时显示“通过/驳回”；管理员只读，提示“审批动作只能由审核员完成”。
  - 驳回弹窗强制填写原因（前端 + 后端双重校验）。
- 409 处理：`request` 拦截器提示冲突；页面捕获 409 后自动刷新任务详情与列表，拿到其他审核员写入后的最新版本。
- 自动刷新：`setInterval(30_000)`，抽屉或操作弹窗打开时暂停，卸载时清理。

### 6.3 AnnotateView 状态动作

- `ASSIGNED` / `REJECTED`：显示“开始标注 / 继续修改”，调用 `POST /start`。
- `IN_PROGRESS`：显示“提交审核”，先保存标注，再 `POST /submit`。
- `SUBMITTED` / `APPROVED`：画布矩形设为不可选中/不可编辑，显示只读提示。
- 保存、删除、绘制、类别/备注等原有 Phase 2 交互全部保留。

### 6.4 管理员分配

- `MyTaskListView` 对 `NEW` 任务显示“分配质检员”，弹窗加载 `GET /users?role=INSPECTOR`，提交 `{inspectorId, version, comment}`。
- 分配成功后刷新列表；409 时拦截器提示版本冲突。

---

## 7. 数据库迁移

`backend/src/main/resources/db/migration/V3__add_review_record_version.sql`：

```sql
ALTER TABLE review_records
    ADD COLUMN version INT NOT NULL DEFAULT 0 AFTER comment;
```

- `vqc_test`：Flyway 自动应用，`Successfully applied 1 migration ... now at version v3`。
- 运行库 `vqc`：重建后端后自动应用，`Current version of schema 'vqc': 3`。
- 未清空 Docker 卷；原有数据保留。

---

## 8. 自动化测试与 E2E 结果

### 8.1 后端全量

命令：`cd backend; mvn -q test`

```
AnnotationIntegrationTest      7/7
ImageUploadIntegrationTest     9/9
ReviewWorkflowIntegrationTest  3/3
TaskConcurrencyTest            2/2
TaskStatusTest                 4/4
VisionqcApplicationTests       1/1
合计 26，Failures 0，Errors 0
```

### 8.2 前端构建

命令：`cd frontend; npm run build`

- 成功；新增 `ReviewView`、`ReviewHistoryView`、`MyTaskListView` chunk。
- 仅有 Vite 的 chunk >500KB 提示（Fabric.js 体积），不是错误。

### 8.3 Phase 2 回归 E2E

命令：`pwsh -NoProfile -File .\scripts\e2e-phase2.ps1`

关键输出：

```
PASS 分配任务 inspectorId=2 version=1
PASS 开始任务 status=IN_PROGRESS version=2
PASS 标注保存 xNorm=0.125 wNorm=0.3
PASS 重启后图片可读且 SHA-256 一致 8714d53c...
PASS 重启后标注仍在 xNorm=0.125
PASS 重复上传幂等 duplicate=True
E2E_RESULT=PASS imageId=3 taskId=3 batchId=3
```

说明：Phase 3 起质检员只能改分配给自己的任务，因此脚本在 Phase 2 原流程中增加了“管理员分配 + 质检员开始”两步；原有 SHA-256、标注持久化、幂等断言未放宽。

### 8.4 Phase 3 全流程 E2E

命令：`pwsh -NoProfile -File .\scripts\e2e-phase3.ps1`

关键输出：

```
PASS 创建批次 id=6 code=B-P3-212818
PASS 上传主流程图片 taskId=8
PASS 分配任务 version=1
PASS 标注并提交 version=3
PASS 质检员/审核员批次筛选接口可用
PASS 待审核列表与批次筛选可见 taskId=8
PASS 驳回 status=REJECTED version=4
PASS 驳回后修改并重新提交 version=6
PASS 审核通过 status=APPROVED version=7
PASS 审核历史完整 ASSIGN,START,SUBMIT,REJECT,START,SUBMIT,APPROVE
PASS 权限矩阵与已通过状态保护
PASS 双审核员并发审批 200/409，最终 APPROVED，APPROVE 记录=1
E2E3_RESULT=PASS taskId=8 concurrencyTaskId=9
```

覆盖项：

- 建批次 → 上传 → 分配 → 开始 → 标注 → 提交 → 驳回 → 修改 → 再提交 → 通过 → 历史。
- 质检员/审核员批次筛选（工作台/任务列表的批次下拉）可用。
- 质检员审批 403；审核员改标注 403；管理员审批 403。
- 已通过任务再提交/再改标注 409。
- reviewer01/reviewer02 双 Session 并发审批，一 200 一 409，最终 APPROVED，APPROVE 记录 1 条。

---

## 9. 人工浏览器验收步骤（用户演示用）

> AI 未执行真实浏览器验收；以下步骤用于人工验证 UI，不替代自动化测试。

### 9.1 访问地址与账号

- 前端：`http://localhost/`（Nginx 80）
- 后端直连：`http://localhost:18080/api/v1`
- 账号：

| 账号 | 密码 | 角色 |
|------|------|------|
| admin | change-me-admin | ADMIN |
| inspector01 | change-me-inspector | INSPECTOR |
| reviewer01 | change-me-reviewer | REVIEWER |
| reviewer02 | change-me-reviewer | REVIEWER |

### 9.2 画布交互（Phase 2.5 重点）

1. 用 `admin` 登录，进入“生产批次”，创建批次；进入“图片管理”，选择批次上传 1 张 JPG/PNG（≤10MB）。
2. 进入“我的质检任务”，对 NEW 任务点“分配质检员”，选择 inspector01。
3. 退出，用 `inspector01` 登录，进入“我的质检任务”，打开标注页。
4. 点“开始标注”（ASSIGNED→IN_PROGRESS）。
5. 点“绘制矩形”，在图片上拖出 **至少 2 个** 矩形；观察右侧标注列表数量。
6. 选中一个矩形，拖动/缩放；选择缺陷类别、填写备注；确认右侧坐标变化。
7. 鼠标滚轮缩放；按住 Alt（或开启“拖动图片”）平移画布。
8. 点“保存标注”；刷新页面（F5），确认矩形、类别、备注、坐标全部恢复。
9. 调整浏览器窗口尺寸，再刷新，确认画布适应窗口、标注位置不偏移。
10. 点“提交审核”，确认状态变为 SUBMITTED，画布变为只读（矩形不可拖动）。
11. 按 F12 打开 Console，确认无红色报错；在 Network 中确认 `PUT /annotations`、`POST /submit` 均 200/2xx。

> 建议截图：登录后任务列表、2 个以上矩形+类别备注、保存后刷新恢复、只读状态、Console/Network 面板。

### 9.3 审核工作台

1. 用 `reviewer01` 登录，进入“审核工作台”，默认显示 SUBMITTED 任务。
2. 点击任务行，查看只读图片与红色缺陷框、标注列表、历史时间线。
3. 点“驳回”，填写原因（不填应被拦截）；确认状态 REJECTED、历史新增一条 REJECT。
4. 用 `inspector01` 登录，打开该任务，点“继续修改”，调整矩形后保存并重新提交。
5. 用 `reviewer01` 登录，打开同一任务，点“通过”；确认 APPROVED、历史 7 条、通过后不能再编辑。
6. “审核历史”页切换 APPROVED / REJECTED，打开时间线。

### 9.4 并发演示（可选）

1. 准备一个 SUBMITTED 任务，记下当前 `version`（可用浏览器 Network 或 API 查看）。
2. 两个浏览器窗口分别登录 reviewer01、reviewer02（不同浏览器/无痕窗口，避免 Session 互相踢下线）。
3. 几乎同时点“通过”。
4. 预期：一个成功提示，另一个提示“任务已被其他用户修改（版本冲突）”，列表/详情自动刷新；数据库中该任务 APPROVE 记录只有 1 条。

---

## 10. 失败案例与修复记录

| 问题 | 现象 | 根因 | 修复 |
|------|------|------|------|
| `/users` 500 | E2E 分配找不到 inspector01 | JPA 元组投影转 record 抛 `ConverterNotFoundException` | 改为 `findAllActive()` + 角色聚合查询，返回 `UserSummary` |
| 批次筛选 403 | 质检员/审核员打开工作台时批次下拉报错 | `SecurityConfig` 把 `/batches/**` 整体限制为 ADMIN，但 GET 批次是筛选依赖 | GET 批次放开给 A/I/R，写操作仍仅 ADMIN；E2E 增加双角色读取断言 |
| 标注旧用例冲突 | 审核员 GET 标注期望 403，实际 200 | Phase 3 审核员必须只读查看缺陷框 | 测试改为“GET 200 + PUT 403”，符合权限矩阵 |
| Phase 2 E2E 403 | 质检员保存标注被拒 | Phase 3 标注要求任务归属 | 脚本增加管理员分配 + 质检员开始两步，断言不放宽 |
| Phase 3 E2E 并发段脚本报错 | `Cannot convert JSON to Int32` | curl `-o` 把响应体写文件，脚本误读响应体当状态码 | 从进程 stdout 读取 `-w` 状态码，响应体保留在文件 |
| Docker 构建偶发失败 | `mvn package` exit 1 | 与本地全量测试并行争用构建资源/网络 | 重试构建成功；最终镜像已重建 |
| 浏览器验收不可用 | bu CDP disconnected；cu 会话锁定/资料页 | 环境限制 + 用户要求自行演示 | 明确标记未验收，给出人工步骤，不假称通过 |

---

## 11. 修改文件清单

### 后端主代码

```
backend/src/main/java/com/visionqc/backend/review/ReviewRecord.java            新增
backend/src/main/java/com/visionqc/backend/review/ReviewRecordRepository.java  新增
backend/src/main/java/com/visionqc/backend/review/ReviewRecordResponse.java    新增
backend/src/main/java/com/visionqc/backend/review/ReviewService.java           新增
backend/src/main/java/com/visionqc/backend/security/Actor.java                 新增
backend/src/main/java/com/visionqc/backend/task/AssignRequest.java             新增
backend/src/main/java/com/visionqc/backend/task/TaskActionRequest.java         新增
backend/src/main/java/com/visionqc/backend/task/TaskService.java               重写状态机
backend/src/main/java/com/visionqc/backend/task/TaskController.java            新增动作接口
backend/src/main/java/com/visionqc/backend/task/TaskRepository.java            search 查询
backend/src/main/java/com/visionqc/backend/annotation/AnnotationService.java   归属/状态校验
backend/src/main/java/com/visionqc/backend/annotation/AnnotationController.java 传 Actor
backend/src/main/java/com/visionqc/backend/config/SecurityConfig.java          按动作授权
backend/src/main/java/com/visionqc/backend/user/UserController.java            新增用户查询
backend/src/main/java/com/visionqc/backend/user/UserRoleRepository.java        findByUserId
backend/src/main/java/com/visionqc/backend/user/SeedDataService.java           reviewer02 演示账号
```

Phase 2.5：

```
backend/src/main/java/com/visionqc/backend/storage/FileStorageService.java     读头/EXIF/decodeMatches
backend/src/main/java/com/visionqc/backend/image/ImageService.java             像素上限/EXIF/清理
backend/src/main/resources/application.yml                                     max-image-pixels
backend/src/test/resources/application-test.yml                                测试上限 500000
```

### 数据库

```
backend/src/main/resources/db/migration/V3__add_review_record_version.sql      新增
```

### 测试

```
backend/src/test/java/com/visionqc/backend/support/TestSupport.java            用户/JPEG/EXIF/清理
backend/src/test/java/com/visionqc/backend/image/ImageUploadIntegrationTest.java  9 例
backend/src/test/java/com/visionqc/backend/annotation/AnnotationIntegrationTest.java 真实用户+归属
backend/src/test/java/com/visionqc/backend/task/ReviewWorkflowIntegrationTest.java  新增 3 例
backend/src/test/java/com/visionqc/backend/task/TaskConcurrencyTest.java           新增 2 例
```

### 前端

```
frontend/src/api/task.js                     assign/start/submit/approve/reject/history/users
frontend/src/views/ReviewView.vue             审核工作台
frontend/src/views/ReviewHistoryView.vue      审核历史时间线
frontend/src/views/MyTaskListView.vue         管理员分配 + 质检员任务
frontend/src/views/AnnotateView.vue           开始/提交/只读锁定
frontend/src/router/index.js                  标注路由收紧为 INSPECTOR
```

### 脚本与文档

```
scripts/e2e-phase2.ps1                       适配 Phase 3 归属，保留原断言
scripts/e2e-phase3.ps1                       新增全流程+并发 E2E
docs/03-状态机与审核流程.md                  更新为实现版
docs/04-API清单.md                           更新已实现 API 与规划项
docs/08-Phase3审核工作流与并发验收.md        本文
README.md                                    增加 reviewer02 演示账号
```

---

## 12. 未实现 / 已知限制

1. **真实浏览器画布验收未由 AI 执行**：按用户要求由用户本人演示；本文不标记为已验收。
2. 用户管理仅实现查询（`GET /users`）；用户创建/编辑/禁用、密码重置为后续规划。
3. 审计日志（`audit_logs`）尚未写入业务动作；当前审核追溯依赖 `review_records`。
4. 质量统计看板、CSV 导出为 Phase 4 规划，未实现。
5. 审核工作台未做 WebSocket 推送，使用 30 秒轮询自动刷新；任务量大时可换 SSE/WebSocket。
6. 未实现审核员转派、多人会签、审核意见模板；当前为单审核员通过/驳回。
7. 已通过任务严格终态，无管理员撤销接口（需求明确“已通过不可撤销”）。
8. 前端仅有构建验证；UI 细节（不同分辨率、极端比例图片）待用户演示确认。

---

## 13. 面试技术亮点（可直接讲）

1. **状态机集中化**：用 `TaskStatus.canTransitionTo` 单一转换表替代散落 if；非法转换在 Service 层统一 409，避免“前端绕过”。
2. **乐观锁双层防护**：显式 `version` 比对负责快速失败与可读提示，JPA `@Version` 负责真实并发下的最终一致；并用两个独立事务测试证明冲突来自数据库版本列。
3. **事务一致性**：`ReviewService.record` 用 `MANDATORY` 强制加入状态变更事务，避免“状态成功、历史丢失”；这是比单纯 `@Transactional` 更强的约束。
4. **权限分层**：SecurityConfig 按动作授权 + Actor 从真实 principal 取 userId + Service 归属校验，三层防线；审核员只读标注、质检员不能审批都在服务端强制。
5. **并发测试不是只测异常处理器**：一个测试走 HTTP 双审核员并发，断言 200/409、最终状态、历史条数；另一个测试用两个 JPA 事务制造真实版本冲突，断言 `OptimisticLockingFailureException` 与最终落库状态。
6. **可追溯性**：每次流转写 `review_records`（taskId/operatorId/action/fromStatus/toStatus/comment/version/时间），历史按 id 正序，前端时间线直接消费。
7. **真实 E2E**：PowerShell + curl 覆盖 7 步闭环、权限矩阵、已通过保护、双 Session 并发；Docker 容器重启后验证文件 SHA-256 与标注持久化。

---

## 14. 复现命令

```powershell
# 后端全量测试
cd backend
mvn -q test

# 前端构建
cd frontend
npm run build

# Docker 重建（不清卷）
cd <repo-root>
docker compose up -d --build

# Phase 2 回归
pwsh -NoProfile -File .\scripts\e2e-phase2.ps1

# Phase 3 全流程 + 并发
pwsh -NoProfile -File .\scripts\e2e-phase3.ps1

# 数据库迁移核对
docker exec vqc-mysql mysql -uroot -p"$env:MYSQL_ROOT_PASSWORD" -D vqc `
  -e "SELECT version, description, success FROM flyway_schema_history ORDER BY installed_rank;"
```
