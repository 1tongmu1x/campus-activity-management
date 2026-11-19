# 公网报名部署

这套配置把 Vue 生产构建、Spring Boot 和 MySQL 放在同一台 Linux 云服务器上。Caddy 在两个域名上自动配置 HTTPS。报名链接使用公开域名，管理员从后台域名工作。现有 MySQL 表可继续使用，云端默认创建独立数据库。

| 入口 | 用途 |
| --- | --- |
| `https://signup.example.com/activities/<活动公开编号>` | 参与者查看活动和报名 |
| `https://admin.example.com/` | 管理员登录、发布活动、查看名单，仅放行配置的来源 IP |
| 容器内 MySQL | 保存活动与报名记录，宿主机不发布数据库端口 |

## 准备服务器

- 准备一台安装 Docker Engine 和 Compose 的 Linux 服务器。将两个域名的 DNS 指向服务器，开放 TCP 80 和 443 给 Caddy。若配置了 AAAA，也要确保服务器的 IPv6 可达。
- 此配置按 Caddy 直连公网设计，后台白名单检查 TCP 对端地址。DNS 服务商如提供代理功能，请用仅 DNS 模式。需要 CDN、负载均衡或共享出口时，先重新确认来源识别方案。动态 IP 管理员可使用固定出口或 VPN，更新白名单后重新创建网关容器。
- 实例先使用模拟活动和模拟联系方式。Spring Boot 2.7 已结束开源维护，正式收集个人数据前应升级到仍受支持的版本，并在目标服务器重新验证注册、登录和并发测试。还需确认反刷阈值、备份与恢复流程，以及页面告知中的联系方式、数据用途和保留期限。先用移动数据验证微信打开与提交。

## 配置并启动

在服务器的仓库根目录复制 `.env.example` 为 `.env`，填写两个不同的 DNS 主机名，以及管理员公网出口的 IP 或 CIDR。管理员地址请写明确的 IP 或尽可能小的网段。域名和管理员用户名可写入 `.env`，密码使用下面的独立文件。

```sh
install -d -m 0700 docker/secrets
```

用受权限保护的本地编辑器分别创建以下文件。每个文件只填一个独立的密码。首位网页管理员密码至少 12 个字符，最多 72 个 UTF-8 字节。目录和密码文件均在 Git 忽略范围内。

| 密码文件 | 容器内读取者 | Linux 权限 |
| --- | --- | --- |
| `docker/secrets/db_root_password` | MySQL 初始化 | 服务器部署账号可读，`0600` |
| `docker/secrets/db_password` | MySQL 初始化和后端 | 后端 UID `10001` 可读 |
| `docker/secrets/admin_password` | 后端首次创建管理员 | 后端 UID `10001` 可读 |

在标准的 Linux Docker Engine（未启用 rootless 或用户命名空间映射）上，可以用以下权限设置供后端读取。Compose 本地文件 secret 使用绑定挂载，保留宿主机文件权限。启用了 UID 映射时，按实际映射设置文件权限。

```sh
chmod 0600 docker/secrets/db_root_password
sudo chown 10001:10001 docker/secrets/db_password docker/secrets/admin_password
sudo chmod 0400 docker/secrets/db_password docker/secrets/admin_password

docker compose config --quiet
docker compose up -d --build
docker compose ps
```

MySQL 和后端健康检查通过后，网关才会启动。需要排查时运行 `docker compose logs --tail 80 db backend gateway`。修改公开域名后需要重新构建网关，因为 Vue 在构建时写入公网页面地址。管理员域名和白名单则由网关启动时读取。

MySQL 密码文件只在新卷初始化时设置账号密码。已有数据库的账号轮换要在 MySQL 里同步更新。后台启动时只在管理员表为空时创建第一个管理员。

## 从异地验证

1. 从白名单 IP 打开后台域名登录，创建并发布一条名额为 1 的测试活动。
2. 点 **分享报名**，确认链接和二维码使用报名域名。手机断开 Wi-Fi，用移动数据从微信打开链接，提交模拟报名。
3. 管理端刷新后，名单出现报名记录且人数变成 1。再次使用不同手机号时，活动应显示已满。
4. 用手机访问报名域名的 `/api/admin/auth/me` 和后台域名首页。公网域名应返回 404，来源未获授权的后台请求应返回 403。

数据库数据和 HTTPS 证书保存在 Compose 命名卷。升级代码前，先备份数据库并确认能够还原。已有 V1、V2 迁移文件保留，表结构变化用新的 Flyway 迁移版本记录。移除容器时保留数据卷。

参考 [Caddy 自动 HTTPS](https://caddyserver.com/docs/automatic-https)、[Docker Compose secrets](https://docs.docker.com/compose/how-tos/use-secrets/) 和 [Spring Boot 2.7 维护说明](https://spring.io/blog/2023/11/23/spring-boot-2-7-18-available-now/)。
