# 管理员与活动管理 API

## 启动后端

在 MySQL 中创建空数据库 `campus_activity`，字符集选 `utf8mb4`。给应用使用的数据库账号授予该库上的建表、迁移及读写权限。首次启动时 Flyway 会执行迁移。

在 PowerShell 中配置数据库连接并设置首个管理员。管理员密码需至少 12 个字符且不超过 72 个 UTF-8 字节。数据库已经有管理员时，启动配置不会覆盖任何wo'yi账号。

```powershell
Set-Location D:\CampusActivityManagement
$env:DB_USERNAME = 'campus_app'
$env:DB_URL = 'jdbc:mysql://localhost:3306/campus_activity?serverTimezone=Asia/Shanghai'
$dbCredential = [pscredential]::new($env:DB_USERNAME, (Read-Host '数据库密码' -AsSecureString))
$env:DB_PASSWORD = $dbCredential.GetNetworkCredential().Password
$env:CAMPUS_ADMIN_USERNAME = 'campus_admin'
$adminCredential = [pscredential]::new($env:CAMPUS_ADMIN_USERNAME, (Read-Host '设置首个管理员密码' -AsSecureString))
$env:CAMPUS_ADMIN_PASSWORD = $adminCredential.GetNetworkCredential().Password
& .\tools\apache-maven-3.9.16\bin\mvn.cmd '-Dmaven.repo.local=D:/CampusActivityManagement/.m2' -f backend/pom.xml spring-boot:run
```

环境变量也可通过 IDE 的启动配置传入。已有管理员时可省略两个 `CAMPUS_ADMIN_` 变量。使用 HTTPS 提供服务时配置 `SESSION_COOKIE_SECURE=true`。

## 登录与会话

1. 调用 `GET /api/admin/auth/csrf`。保留服务器返回的会话 Cookie，并从响应读取 `headerName` 和 `token`。
2. 调用 `POST /api/admin/auth/login`。使用 `application/x-www-form-urlencoded` 提交 `username` 和 `password`，随请求发送 Cookie 与上一步的 CSRF 请求头。登录成功返回 204，凭据无效返回 401。
3. 使用登录后返回的会话 Cookie，重新调用 `GET /api/admin/auth/csrf` 获取令牌。活动写请求与退出请求都要携带该请求头。
4. `GET /api/admin/auth/me` 查询当前账号，`POST /api/admin/auth/logout` 注销会话。

## 活动接口

下列路径均以 `/api/admin/activities` 开头，全部需要已登录的管理员会话。

| 方法及相对路径 | 作用 | 成功状态 |
| --- | --- | --- |
| `GET /`（使用基础路径，无末尾斜杠） | 按条件分页查询 | 200 |
| `GET /{id}` | 查询详情 | 200 |
| `POST /`（使用基础路径，无末尾斜杠） | 创建草稿 | 201 |
| `PUT /{id}?version=…` | 编辑活动 | 200 |
| `POST /{id}/publish` | 发布草稿，重复提交返回当前数据 | 200 |
| `DELETE /{id}?version=…` | 逻辑删除活动 | 204 |

列表参数为 `type`、`status`、`from`、`to`、`page` 和 `size`。时间范围按活动开始时间筛选，包含 `from`，不包含 `to`。页码从 0 开始，默认每页 20 条，最多 100 条。响应是按开始时间降序排列的活动数组。

创建与编辑使用相同的 JSON 字段。

```json
{
  "title": "就业指导讲座",
  "activityType": "LECTURE",
  "description": "介绍简历准备与面试技巧",
  "startsAt": "2027-01-12T14:00:00",
  "endsAt": "2027-01-12T15:30:00",
  "location": "教学楼 A101",
  "registrationDeadline": "2027-01-12T12:00:00",
  "capacity": 80
}
```

时间均为北京时间，精确到秒。活动结束时间须晚于开始时间，报名截止时间不得晚于活动开始时间。发布时两者须尚未到期。响应在上述字段之外返回 `id`、`publicCode`、`status`、`publishedAt`、`registeredCount` 和 `version`。

状态可能是 `DRAFT`、`REGISTRATION_OPEN`、`REGISTRATION_CLOSED`、`FINISHED` 或 `CANCELLED`。编辑和删除应传最近读到的 `version`。版本冲突或容量低于当前有效报名人数时返回 409。活动删除后详情返回 404，报名记录仍保留在数据库中。

## 运行集成测试

准备独立的 MySQL 测试库，按上面的方式传入该库的连接参数和管理员凭据。设置 `CAMPUS_RUN_MYSQL_IT=true`，然后运行 Maven `test`。测试会执行迁移，并在测试库中写入活动和虚构报名记录。
