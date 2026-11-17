# VisionQC — 工业视觉质检与缺陷复核平台

面向工业零件图片的质检业务系统：图片批次管理 → 人工缺陷标注 → 审核工作流 → 质量统计与追踪。
项目目的是展示完整全栈能力（Java + Spring Boot + MySQL + Vue 3），不做 AI 模型、不做相机控制。

## 目录结构

```
vqc/
├── docs/                # 设计文档（01 需求架构 / 02 数据库 / 03 状态机 / 04 API / 05 前端 / 06 阶段计划 / 07 Phase 2 学习说明 / 08 Phase 3 审核与并发验收）
├── scripts/             # e2e-phase2.ps1、e2e-phase3.ps1 端到端验收脚本
├── backend/             # Spring Boot 3 + Java 17 + JPA + Flyway + Spring Security
├── frontend/            # Vue 3 + Vite + Pinia + Element Plus + ECharts + Fabric.js
├── .env.example         # 本地演示环境变量模板（复制为 .env）
└── docker-compose.yml   # MySQL + 后端 + 前端
```

## 环境要求

| 组件 | 版本 |
|------|------|
| JDK | 17 |
| Maven | 3.9+ |
| Node.js | 18+ |
| MySQL | 8.0+（本机为 9.7） |

## 快速启动

### 方式一：Docker 一键部署（推荐）

```powershell
# 首次运行复制环境变量模板，并按需修改密码
Copy-Item .env.example .env
# 编辑 .env 后启动
docker compose up -d --build
```

启动后：

| 服务 | 地址 | 说明 |
|------|------|------|
| 前端 | http://localhost | Nginx 托管 + /api 反代（同源，Session 认证） |
| 后端 | http://localhost:18080 | Swagger: /swagger-ui.html |
| MySQL | localhost:3307 | root / `${MYSQL_ROOT_PASSWORD}`，业务库 vqc（用户 `${MYSQL_APP_USER}`） |

> 宿主 18080/3307 避开常见本地 8080/3306 端口。
> 常用命令：`docker compose ps` 看状态、`docker compose logs -f backend` 看日志、`docker compose down` 停止（数据保留在卷 vqc_vqc-mysql-data）。

### 方式二：本地开发运行

#### 1. 初始化数据库（一次性）

```powershell
mysql -uroot -p
# 输入密码后执行：
CREATE DATABASE IF NOT EXISTS vqc DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

#### 2. 启动后端（首次启动 Flyway 自动建表并种入角色；种子密码由 APP_ADMIN_PASSWORD 控制）

```powershell
cd backend
$env:DB_PASSWORD = "你的MySQL密码"
$env:APP_ADMIN_PASSWORD = "你的演示管理员密码"
mvn spring-boot:run        # http://localhost:8080，Swagger: /swagger-ui.html
```

#### 3. 启动前端

```powershell
cd frontend
npm install                # 首次
npm run dev                # http://localhost:5173，代理 /api → 8080
```

### 登录验证

浏览器打开 http://localhost → 用 `.env` / 环境变量中配置的种子账号登录（**仅本地演示**，不要暴露到公网）：

| 账号 | 密码来源 | 角色 | 说明 |
|------|----------|------|------|
| admin | `APP_ADMIN_PASSWORD` | ADMIN | 设备/批次/图片上传 |
| inspector01 | `APP_INSPECTOR_PASSWORD` | INSPECTOR | 质检员，画框标注 |
| reviewer01 | `APP_REVIEWER_PASSWORD` | REVIEWER | 审核员 |
| reviewer02 | `APP_REVIEWER_PASSWORD` | REVIEWER | 第二审核员（并发演示） |

> 演示账号由 `APP_SEED_ADMIN` / `APP_SEED_DEMO` 控制。仓库不包含真实密码；生产部署请设 `APP_SEED_ADMIN=false`、`APP_SEED_DEMO=false`。

## 测试

```powershell
# 后端全部测试（单元 + 真实 MySQL 集成；需 Docker MySQL 3307 在运行）
cd backend
$env:TEST_DB_PASSWORD = "测试库密码"
mvn test

# 只跑集成测试
mvn "-Dtest=ImageUploadIntegrationTest,AnnotationIntegrationTest" test

# 前端构建验证
cd frontend
npm run build
```

集成测试使用独立库 `vqc_test`（自动创建，不影响开发库 `vqc`），文件写临时目录，不污染 Docker 卷。

### 端到端验收（真实 HTTP + 容器重启持久化 + 审核闭环）

```powershell
cd <repo-root>
# 账号密码从环境变量读取；未设置时使用 .env.example 中的占位值
$env:VQC_ADMIN_PASSWORD = "change-me-admin"
$env:VQC_INSPECTOR_PASSWORD = "change-me-inspector"
$env:VQC_REVIEWER_PASSWORD = "change-me-reviewer"
# Phase 2 回归（管理员建批次→上传→分配→质检员开始→标注→重启→SHA/标注持久化）
pwsh -NoProfile -File .\scripts\e2e-phase2.ps1
# Phase 3 全流程（分配→标注→提交→驳回→修改→再提交→通过→历史 + 双审核员并发）
pwsh -NoProfile -File .\scripts\e2e-phase3.ps1
```

覆盖：Session + CSRF、真实上传、标注归属、完整状态机、权限矩阵、审核历史、JPA 乐观锁并发（200/409）。

## 当前进度（Phase 3）

| 能力 | 状态 |
|------|------|
| 设计文档（架构/ER/状态机/API/计划/Phase 2 学习说明/Phase 3 验收） | ✅ |
| 数据库迁移脚本（11 表 + 缺陷类别种子 + V3 审核记录版本） | ✅ |
| 登录认证（Session + BCrypt + 角色权限 + CSRF） | ✅ |
| 设备管理 CRUD + 启停 | ✅ |
| 生产批次 CRUD + 关闭 | ✅ |
| 图片上传（单/多图、格式/大小/像素/EXIF 校验、SHA-256、宽高、去重、分页、安全预览） | ✅ |
| 文件-DB 一致性（STAGED/ACTIVE/FAILED + 定时清理 + 原子移动） | ✅ |
| 质检任务列表 + 图片上传自动建任务 + 管理员分配 | ✅ |
| Fabric.js 矩形标注（缩放/平移/画框/编辑/删除/类别/备注/保存/刷新恢复） | ✅ |
| 归一化坐标 DECIMAL(8,6) 存储 + 服务端越界/类别/状态校验 | ✅ |
| Phase 3 审核状态机 + 权限矩阵 + 审核历史 + JPA 乐观锁并发 | ✅ |
| 前端审核工作台 / 审核历史时间线 / 提交与驳回交互 | ✅ |
| 自动化测试 26 条 + Phase 2/3 真实 E2E | ✅ |
| 真实浏览器画布人工验收 | ⏳ 用户自行演示（步骤见 docs/08 第 9 节） |
| 质量看板 / AI 模型 | ⏳ Phase 4（任务书要求暂不做） |

详细实现与验收：`docs/07-Phase2学习与实现说明.md`、`docs/08-Phase3审核工作流与并发验收.md`。

## 核心设计速览

- **认证**：Session 方案（单体部署不需要 JWT，理由见 SecurityConfig 注释）
- **并发审核**：`inspection_tasks.version` + JPA `@Version`，冲突返回 HTTP 409
- **状态机**：`NEW→ASSIGNED→IN_PROGRESS→SUBMITTED→APPROVED`，驳回走 `REJECTED→IN_PROGRESS`，非法转换由 `TaskStatus` 枚举集中拒绝
- **图片一致性**：文件与元数据分离，服务端 UUID 文件名 + SHA-256 批内去重（Phase 2 实现暂存/原子 rename 补偿）
- **坐标**：归一化 DECIMAL(8,6) 存储，缩放不漂移

详细设计：`docs/02-数据库设计.md`、`docs/03-状态机与审核流程.md`
