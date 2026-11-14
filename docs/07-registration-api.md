# 公开活动与报名接口

参与者无需管理员账号。通过活动的 `publicCode` 获取已发布活动和提交报名。时间按北京时间处理。已截止或已结束的活动仍可查看详情，草稿、取消和逻辑删除的活动返回 404。

| 请求 | 返回 |
| --- | --- |
| `GET /api/public/activities/{publicCode}` | 200，活动名称、类型、简介、地点、时间、状态及 `registrationFull` |
| `GET /api/admin/auth/csrf` | 200，提供匿名会话也可使用的 CSRF 令牌，保存服务器返回的 Cookie |
| `POST /api/public/activities/{publicCode}/registrations` | 201，报名成功，响应体为空 |

报名请求使用同源 Cookie 以及令牌响应里的 `headerName` 和 `token`。请求体为 JSON，字段为 `name`（姓名，最多 64 字符）、`phone`（11 位中国大陆手机号码）和 `college`（学院，最多 100 字符）。服务端会去除字段两端的空白。

- 400 `INVALID_REGISTRATION`：字段缺失或格式错误。
- 403：会话或 CSRF 令牌无效。
- 404 `ACTIVITY_NOT_FOUND`：活动不存在或不可公开访问。
- 409 `REGISTRATION_CLOSED`：报名截止或活动已结束。
- 409 `DUPLICATE_REGISTRATION`：该手机号已报名。
- 409 `ACTIVITY_FULL`：报名名额已满。

上述带错误码的响应使用 `code` 字段区分原因。公开详情不返回内部 ID、编辑版本、报名名单或手机号。

报名事务锁定活动行，取得锁后再读取当前时间、查询报名人数和判断是否可以插入。MySQL 唯一约束同时保护活动与手机号的组合。当前功能复用 V1 的报名表，数据库迁移版本仍为 V2。

[运行 MySQL 集成测试](05-admin-api.md#运行集成测试)。测试覆盖匿名访问、已删除活动、非法手机号、缺少 CSRF、重复报名、满员、截止后报名及最后一个名额的并发竞争。
