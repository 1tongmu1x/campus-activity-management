# 高校学生活动全流程管理系统

面向高校讲座、宣讲会、就业指导会等活动的发布、报名和签到管理系统。普通参与者无需注册，可通过公开链接或二维码完成报名和签到；活动管理员负责维护活动与查看数据。

> 当前状态：活动管理后台和公开报名已实现。参见 [本机试用步骤](docs/06-admin-web.md)、[报名接口](docs/07-registration-api.md) 和 [公网部署指南](docs/08-public-deployment.md)。

## 功能范围

- 管理员登录、活动创建、编辑、删除、发布与筛选
- 公开活动详情和报名页面
- 报名人数上限、报名截止时间、手机号重复报名等校验
- 报名二维码、报名名单及人数统计
- 现场签到与签到统计（后续迭代）

详细需求见 [docs/01-requirements.md](docs/01-requirements.md)，开发路线见 [docs/03-roadmap.md](docs/03-roadmap.md)。

## 技术栈

- 后端：Java 8、Spring Boot 2.7、Spring Security、Spring JDBC、Flyway、MySQL 8
- 前端：Vue 3、Vite、Element Plus 图标
- 文档：OpenAPI / Swagger（将在接口开发时接入）
- 部署：Docker Compose、Caddy

## 目录说明

```text
├── backend/       # Spring Boot API
├── frontend/      # Vue 3 前端
├── docs/          # 需求、架构、数据库与接口文档
├── docker/        # 镜像构建与网关配置
└── .github/       # GitHub 协作模板（后续完善）
```

## 本地运行前置条件

需要 Java 8、Maven 3.8+、Node.js LTS、Git 和 MySQL 8。本机的便携工具位于 D 盘项目的 `tools/` 目录。新克隆的仓库使用自行安装的开发工具或按本地脚本约定放置便携工具。

PowerShell 下可运行 `powershell -ExecutionPolicy Bypass -File .\\scripts\\check-environment.ps1` 验证环境，运行 `powershell -ExecutionPolicy Bypass -File .\\scripts\\build.ps1` 构建项目。该命令只对当前进程绕过本地脚本执行限制，不会修改 Windows 的全局安全策略。脚本将 Maven 与 npm 的本地依赖缓存保存在项目目录，不写入 C 盘用户缓存。

## 开源许可证

本项目采用 [Apache License 2.0](LICENSE)。允许商业使用、修改和再发布；再发布时必须保留许可证、版权声明和 [NOTICE](NOTICE) 中与本项目有关的署名信息。
