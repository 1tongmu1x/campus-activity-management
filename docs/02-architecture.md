# 架构设计（v0.1）

```text
浏览器（Vue 3）
       │ HTTP / JSON
       ▼
Spring Boot REST API
       │ Spring JDBC
       ▼
    MySQL 8
```

## 后端分层

- `controller`：接收请求、返回 HTTP 响应。
- `service`：执行业务规则，例如人数上限和重复报名校验。
- `repository`：数据库访问。
- 接口入参与出参独立定义，数据库查询使用参数绑定。

## 安全原则

- 管理员密码采用 BCrypt 哈希保存。
- 管理员使用 Cookie 会话，写请求须携带 CSRF 令牌。
- 报名页面只使用不可预测的公开标识（活动 `publicCode`），不暴露内部自增 ID。
- 手机号在管理端按权限显示，日志不得记录完整个人信息。
- 数据库账号、密码等敏感配置只通过环境变量或本地忽略文件提供。

## 状态规则

活动实体保存业务状态 `DRAFT` / `PUBLISHED` / `CANCELLED`。面向参与者的展示状态根据当前时间推导：未发布、报名中、报名截止、已结束。
