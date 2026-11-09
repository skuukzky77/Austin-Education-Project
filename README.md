# Student Desk · 学生管理系统

一个面向课程顾问的纵向切片：**咨询建档 → 安排试听 → 记录结果 → 跟进**。项目使用 React + TypeScript、Java 21 / Spring Boot 与 SQLite，方案及范围见 [DESIGN.md](DESIGN.md)。

## 部署说明

项目使用 **Docker + Render** 进行在线部署，通过 GitHub `main` 分支自动同步和部署。

- **部署平台：** [Render](https://render.com/)
- **部署方式：** Docker
- **部署配置：** `Dockerfile`、`render.yaml`
- **前端构建：** Node.js 22
- **后端运行：** Java 21 + Spring Boot 3.5.7
- **数据库：** SQLite

### 在线演示

访问地址：[Austin Education 学生管理系统](https://austin-student-management-demo-o4x0.onrender.com)

温馨提示： 项目部署在 Render Free 免费服务上，连续 15 分钟没有访问时，服务会自动休眠。休眠后首次访问通常需要等待约 1 分钟完成启动，请耐心等待，页面暂时无法打开不一定代表服务异常。

### 部署架构

前端 React 项目构建后由 Spring Boot 提供静态资源服务，后端同时提供 REST API 接口，使用 SQLite 存储业务数据。

### 数据持久化说明

当前使用 Render Free 服务，SQLite 数据库存储于临时文件系统中，服务重启或重新部署后数据可能丢失。因此，当前部署仅用于功能演示和学习，不建议用于生产环境或存储真实用户数据。
## 本地运行

需要 Java **JDK 21**（不仅是 Java 运行时）、Node.js 20.19+ 或 22.12+、npm，以及首次安装依赖时的网络。Maven Wrapper 会自动下载 Maven 3.9.11，无需事先安装 Maven。

在两个终端中分别运行：

```bash
cd backend
./mvnw spring-boot:run
```

Windows 使用 `mvnw.cmd spring-boot:run`。后端监听 `http://localhost:8080`，首次启动会在 `backend/student-management.db` 创建 SQLite 数据库和演示数据。

```bash
cd frontend
npm ci
npm run dev
```

打开前端启动命令显示的地址（默认 `http://localhost:5173`）。开发服务器将同源 `/api` 请求代理到后端；浏览器不需要单独配置 CORS。所有业务日期与时间均为 **Australia/Melbourne**。

| 角色 | 账号 | 密码 | 演示内容 |
|---|---|---|---|
| 顾问 | `counselor` | `demo1234` | 30 名学生、待办、试听排期 |
| 另一位顾问 | `counselor2` | `demo1234` | 验证无法查看或操作前一位顾问的学生详情 |
| 老师 | `teacher` | `demo1234` | 查看自己当天的试听和正式课 |

这些是**仅供本地演示的固定账号和密码**。当前应用没有注册、密码重置、登录限流或生产部署配置，不应使用真实学生数据或原样公开部署。数据库只在第一次启动的空库中自动 seed；重置演示数据时先停止后端，再删除 `backend/student-management.db`（以及同名 `-wal`、`-shm` 文件，如存在），重启即可。删除会清除本地全部演示数据。

## 演示流程

1. 用 `counselor` 登录，首页会提醒已结束但未录入结果的试听，可直接点击“记录结果”；同时可查看待跟进和试听排期。在“我的学生”中可搜索其他顾问的学生，但不能打开其详情。
2. 新建学生及联系人，在学生详情安排试听。系统检查学生和老师与试听、已存在正式课次的时间冲突。
3. 试听结束后，记录结果并填写下一步跟进及联系时间，或选择“无需后续跟进”并给出原因。为方便演示，可将试听安排在过去的未冲突时间；未来试听不能提前记录结果。
4. 在学生详情或首页处理待办并填写联系结果；重复提交会被拒绝。试用另一顾问及老师账号，检查访问边界。
5. 在结果表单输入反馈，可点击“AI 辅助整理建议”；用户决定是否采用，未配置或失败时仍可手动填写。

## AI 配置：本地模型（无需 API 密钥）

本地方案使用 `llama.cpp` 和 [Qwen2.5-1.5B-Instruct](https://huggingface.co/Qwen/Qwen2.5-1.5B-Instruct-GGUF)（Apache 2.0）。首次启动需从 Hugging Face 下载约 **1.04 GiB** 的模型，之后可重复使用；模型文件不包含在项目 ZIP 或 Git 仓库中。小模型的建议质量有限，使用前须由顾问核对。

在 macOS 上先安装 [Homebrew](https://brew.sh/)，然后打开第一个终端：

```bash
brew install llama.cpp
cd ~/Downloads/student-management
chmod +x scripts/start-local-model.sh
./scripts/start-local-model.sh
```

脚本会校验模型的 SHA-256，并在本机 `127.0.0.1:8081` 启动服务；保持窗口打开。在第二个终端配置并启动后端：

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
export PATH="$JAVA_HOME/bin:$PATH"
export OPENAI_BASE_URL=http://127.0.0.1:8081/v1
export OPENAI_MODEL=qwen-local
unset OPENAI_API_KEY
cd ~/Downloads/student-management/backend
./mvnw spring-boot:run
```

第三个终端按“本地运行”章节启动前端，登录后在待录入结果的试听中输入反馈，点击“AI 辅助整理建议”。在这种模式下反馈只发送到本机模型服务；服务器未启动或调用失败时可手动填写。

已在 Linux CPU 环境使用 `llama.cpp` b12211 和 Qwen2.5-1.5B-Instruct Q4_K_M，**无 API 密钥**完成真实推理，并通过浏览器验证人工采用及保存流程；macOS 步骤尚需在目标设备上验证。生成可能需要数秒，取决于电脑性能。

## AI 配置：远程模型（可选）

后端启动前设置环境变量；不设置也可完整演示业务流程。

```bash
export OPENAI_API_KEY='你的密钥'
# 可选：默认 gpt-4o-mini；需要支持 Chat Completions 的 JSON Schema structured output
export OPENAI_MODEL='gpt-4o-mini'
# 可选：默认 https://api.openai.com/v1
# export OPENAI_BASE_URL='https://api.openai.com/v1'
```

服务端只发送用户主动填写的试听反馈，要求 `summary` 和 `suggestedAction` 的 JSON Schema 输出，并校验字段、类型和长度；结果不自动保存、发消息或修改状态。远程服务没有密钥、超时、服务异常或校验失败时提示手动填写。不要将密钥、真实反馈或数据库提交到仓库。代理环境需确保 JVM 可通过配置的 HTTPS 代理访问服务；不允许向非本机的 HTTP 地址发送请求。

## 验证

```bash
cd backend
./mvnw test
```

测试覆盖未登录和跨顾问访问、老师权限、试听与正式课冲突及首尾相接、并发争抢同一老师、试听与跟进事务、试听结果待录入提醒、重复操作、墨尔本夏令时无效/歧义时间，以及 LLM 输出校验与失败降级。默认自动化测试使用本地模拟服务，不消耗 API 额度。

如需单独验证真实本地模型，请先启动 `scripts/start-local-model.sh`，再执行：

```bash
cd backend
RUN_REAL_LLM_TEST=true ./mvnw -Dtest=LocalModelIntegrationTest test
```

```bash
cd frontend
npm ci
npm test
npm run build
```

## 实现与边界

- 身份使用服务端 HttpOnly、SameSite=Strict 会话；写操作要求自定义请求头，服务端每次检查用户是否启用，并依据角色和学生负责人验证资源权限。生产使用需要 HTTPS、Secure Cookie、限流、账号管理及更完整的安全审查。
- 试听冲突和写入发生在同一数据库事务中；当前单进程以 SQLite 单连接串行化写入，同一学生只能有一条已安排试听。已取消课程不占时段，允许前后紧邻。正式课的种子数据仅供冲突检查及老师查看，没有正式课管理界面。
- 试听完成与跟进创建在同一事务内完成；一条试听最多关联一条跟进。课时、报名、出勤、负责人转交、支付和自动通知都在设计规划中，**本切片未实现**。
- 试听结束后若仍未录入结果，顾问首页显示待处理提醒；页面在前台时每分钟刷新，并非站外推送。录入结果或取消试听后提醒消失。
- 数据库保存 UTC 毫秒，接口接收和展示墨尔本本地时间；夏令时转换中不存在或有歧义的时间会被拒绝，需选择其他时间。
- 登录、学生、排课和跟进界面提供加载、空和错误状态。仓库现已提供 Render 部署配置；按下方步骤创建服务后才会获得真实在线地址。

## AI 编程工具使用说明

使用 Codex 协助梳理需求、讨论并编写设计文档、实现代码和测试，以及进行浏览器验证。设计中选择把垂直切片集中在咨询、试听和跟进；没有采用把试听、正式班级、课时和续费都做成完整页面的扩展方案，因为固定时间内应优先保证一条流程可运行、规则可验证。仓库保留完整提交历史；使用者应通读并理解设计判断、实现和测试，再进行展示。


## Render 在线演示部署（AI 暂时关闭）

仓库根目录提供 `Dockerfile` 和 `render.yaml`，用于把前端静态资源打包进 Spring Boot，由同一个 Web 服务提供页面和 `/api` 接口。部署使用 SQLite，不需要配置 OpenAI API Key。

### 创建服务

1. 登录 [Render](https://render.com/)，选择 **New → Blueprint**。
2. 连接 GitHub 仓库 `skuukzky77/Austin-Education-Project`，选择 `main` 分支。
3. Render 读取仓库根目录的 `render.yaml`，确认创建 `austin-student-management-demo` 服务。
4. 等待 Docker 构建和部署完成。Render 分配的 `onrender.com` 地址就是演示地址；打开根路径应显示学生管理系统登录页。
5. 使用下方演示账号登录，检查学生列表、学生详情、试听和跟进。AI 辅助整理入口在未配置模型密钥时会提示手动填写，不会请求远程模型。

### 演示环境的重要限制

- 当前 Blueprint 使用 Render Free Web Service 和 `/tmp/student-management.db`。**免费实例没有持久化磁盘**：服务休眠、重启、重新部署或实例替换后，数据库可能被清空并重新生成演示数据。请只使用虚构演示信息，不要录入真实学生或家长资料。
- 演示账号和密码是仓库公开可见的固定凭据。任何访问者都可以尝试登录；该服务只用于公开作品演示，不是生产系统。
- 不要在 Render 环境变量中添加 `OPENAI_API_KEY`。当前 AI 服务在未配置密钥且目标不是本机模型时会直接返回“未配置 AI 服务”，业务表单仍可手动填写。
- SQLite 单连接适用于当前单实例演示，不适合横向扩容或多实例并发。正式使用前需迁移到受管理的数据库，并完善持久化、HTTPS Cookie、安全审查、登录限流、账号管理和隐私保护。
- 当前部署配置仅用于第一阶段在线演示；课时、报名、出勤等尚未实现的模块不会因为部署而自动具备。

### 本地构建验证

在仓库根目录执行：

```bash
docker build -t student-management-demo .
docker run --rm -p 8080:8080 -e DATABASE_URL=jdbc:sqlite:/tmp/student-management.db student-management-demo
```

浏览器打开 `http://localhost:8080`。此 Docker 构建配置尚未在本次环境中实际运行验证；首次部署后请查看 Render 的 Build Logs 和 Runtime Logs，如构建失败请先不要分享在线地址。
