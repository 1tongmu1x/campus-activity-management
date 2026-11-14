# 数据库设计（v0.1）

数据库名为 `campus_activity`，字符集使用 `utf8mb4`。时间字段采用 MySQL `DATETIME`，系统统一按照 `Asia/Shanghai` 处理。

## 实体关系

```text
admin_user 1 ──── N activity 1 ──── N registration 1 ──── 0..1 check_in
                              │
                              └──── N activity_attachment（后续）
```

## 表职责

| 表 | 职责 | 关键约束 |
| --- | --- | --- |
| `admin_user` | 活动管理员账号 | `username` 唯一；仅保存 BCrypt 密码哈希 |
| `activity` | 活动基本资料和发布状态 | `public_code` 唯一；容量必须大于 0；使用版本号支持并发控制 |
| `registration` | 参与者报名资料 | `activity_id + phone` 唯一，防止同一活动重复报名 |
| `check_in` | 现场签到记录 | `registration_id` 唯一，确保一人仅签到一次 |

## 关键业务规则如何落地

- **报名时间**：服务层判断 `now < registration_deadline`；数据库保存截止时刻。
- **人数上限**：报名功能实现时，在事务中锁定所属活动行，再检查状态、有效报名人数并插入报名。管理端已经用同一活动行锁串行处理编辑和删除。
- **重复报名**：数据库唯一索引是最终防线，服务层先给出友好提示。
- **删除活动**：第一版采用逻辑删除 `deleted_at`，既能满足删除操作，也不会破坏已有报名和签到记录。
- **状态展示**：`DRAFT`、`PUBLISHED`、`CANCELLED` 是人工状态；“报名中”“报名截止”“已结束”按当前时间推导，不重复保存。
- **发布时间**：`published_at` 由 V2 迁移添加，首次发布时赋值。为迁移前已经发布的活动使用原 `updated_at` 回填。

## 隐私处理

手机号只出现在管理员受保护的接口响应中；公开活动页面不会返回报名名单。提交到 GitHub 的配置文件不得包含真实数据库密码或真实学生信息。
