# VisionQC API 清单 V1

统一约定：
- 基础路径 `/api/v1`，JSON；认证用 Session Cookie（登录后 `JSESSIONID`）。
- 权限标注：A=ADMIN, I=INSPECTOR, R=REVIEWER。
- 分页参数 `page`(0 起) `size`(默认 20)，响应 `{content, totalElements, page, size}`。
- 错误响应统一 `{code, message}`；409 = 状态冲突/版本冲突。
- **CSRF**：除 GET 外的写请求需带 `X-XSRF-TOKEN` 头；前端先 `GET /auth/csrf` 获取 token（同时下发 `XSRF-TOKEN` Cookie）。

## 1. 认证与用户

| 方法 | 路径 | 权限 | 说明 |
|------|------|------|------|
| POST | /auth/login | - | 登录，成功写 Session |
| POST | /auth/logout | - | 登出，invalidate Session |
| GET | /auth/csrf | - | 下发 CSRF token（登录页启动时调用） |
| GET | /auth/me | 登录 | 当前用户信息+角色 |
| GET | /users | A | 分页查询用户；`role`/`keyword` 过滤（Phase 3 已实现）；不返回密码 |
| POST | /users | A | 创建用户（规划，尚未实现） |
| PUT | /users/{id} | A | 编辑用户/启用禁用/重置密码（规划，尚未实现） |
| GET | /users/{id} | A | 用户详情（规划，尚未实现） |

## 2. 设备

| 方法 | 路径 | 权限 | 说明 |
|------|------|------|------|
| GET | /devices | A | 分页+关键字搜索 |
| POST | /devices | A | 创建（device_code 唯一） |
| PUT | /devices/{id} | A | 编辑 |
| PATCH | /devices/{id}/status | A | 启用/停用 |
| GET | /devices/{id}/batches | A | 设备关联批次 |

## 3. 生产批次

| 方法 | 路径 | 权限 | 说明 |
|------|------|------|------|
| GET | /batches | A/I/R | 分页（按设备/日期/状态过滤）；审核工作台/质检任务筛选用 |
| POST | /batches | A | 创建（关联 device） |
| GET | /batches/{id} | A/I/R | 详情 |
| POST | /batches/{id}/close | A | 关闭批次 |

## 4. 图片

| 方法 | 路径 | 权限 | 说明 |
|------|------|------|------|
| POST | /images/upload/{batchId} | A | 多图上传 multipart，SHA-256 批内去重，幂等返回已有记录 |
| GET | /images | A/I/R | 分页（按批次/状态过滤） |
| GET | /images/{id}/file | 登录 | 输出图片二进制（流式，校验归属） |
| GET | /images/{id} | A/I/R | 元数据详情 |
| DELETE | /images/{id} | A | 逻辑删除 |

## 5. 缺陷分类

| 方法 | 路径 | 权限 | 说明 |
|------|------|------|------|
| GET | /defect-categories | 登录 | 列表（标注选择用） |
| POST/PUT/DELETE | /defect-categories/... | A | 管理 |

## 6. 质检任务与审核

| 方法 | 路径 | 权限 | 说明 |
|------|------|------|------|
| GET | /tasks | A/I/R | 分页；`status`、`batchId` 过滤；质检员自动只看自己的任务 |
| GET | /tasks/{id} | A/I/R | 任务详情（含 version） |
| GET | /tasks/{id}/history | A/I/R | 审核历史（review_records，按时间正序） |
| POST | /tasks/{id}/assign | A | 分配质检员，body `{inspectorId,version,comment}`；NEW→ASSIGNED |
| POST | /tasks/{id}/start | I | 开始/继续修改，body `{version,comment}`；ASSIGNED/REJECTED→IN_PROGRESS |
| POST | /tasks/{id}/submit | I | 提交审核，body `{version,comment}`；需≥1标注；IN_PROGRESS→SUBMITTED |
| POST | /tasks/{id}/approve | R | 通过，body `{version,comment}`；SUBMITTED→APPROVED；禁自审 |
| POST | /tasks/{id}/reject | R | 驳回，body `{version,comment}`；原因必填；SUBMITTED→REJECTED |
| GET | /tasks/{id}/annotations | A/I/R | 读取标注（审核员只读） |
| PUT | /tasks/{id}/annotations | I | 全量替换标注；仅归属人且任务可编辑时允许 |
| GET | /audit-logs | A | 审计日志分页（规划，尚未实现） |

## 7. 统计与报表（Phase 4 规划，尚未实现）

> 以下接口当前未落地，保留为后续阶段设计目标，避免与已实现 API 混淆。

| 方法 | 路径 | 权限 | 说明 |
|------|------|------|------|
| GET | /stats/overview | A/R | 总数/已审/待审/缺陷图片数 |
| GET | /stats/defect-distribution | A/R | 各缺陷类别分布 |
| GET | /stats/defect-trend?days=30 | A/R | 每日缺陷图片趋势 |
| GET | /stats/device-quality | A/R | 各设备不良率 |
| GET | /stats/batch-quality | A/R | 各批次不良率 |
| GET | /export/batch-quality.csv | A | CSV（UTF-8 BOM，防公式注入） |
| GET | /export/review-results.csv | A | CSV |
| GET | /export/defect-stats.csv | A | CSV |

## 8. OpenAPI

- springdoc-openapi，`/swagger-ui.html` 自动生成；API 文档随代码演进，不手写重复版本。
