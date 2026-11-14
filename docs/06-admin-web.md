# 在本机试用活动管理后台

## 1. 准备数据库账号

在 Workbench 连接创建了 `campus_activity` 的本机 MySQL 实例。如果还没有项目专用账号，用有授权权限的连接执行下列语句。执行前把密码占位符替换成你自己的强密码。

```sql
CREATE USER 'campus_app'@'127.0.0.1' IDENTIFIED BY '替换为你的数据库密码';
GRANT ALL PRIVILEGES ON `campus\_activity`.* TO 'campus_app'@'127.0.0.1';
```

这里的数据库账号只用于后端连接和运行该库的 Flyway 迁移。网页管理员是另一个账号，由后端首次启动时创建。

## 2. 启动后端

按 [后端启动步骤](05-admin-api.md#启动后端) 在 PowerShell 中填入专用数据库账号的密码，并设置首次登录所用的管理员账号和密码。

使用上面创建的账号时，将启动步骤中的数据库地址设为 `jdbc:mysql://127.0.0.1:3306/campus_activity?serverTimezone=Asia/Shanghai`。看到 Spring Boot 启动成功后，保持窗口打开。

## 3. 启动前端

另开一个 PowerShell 窗口。

```powershell
Set-Location D:\CampusActivityManagement
$nodeDir = 'D:\CampusActivityManagement\tools\node-v26.13.1-win-x64'
$env:Path = "$nodeDir;$env:Path"
$env:npm_config_cache = 'D:\CampusActivityManagement\.npm-cache'
& "$nodeDir\npm.cmd" --prefix frontend run dev -- --host 127.0.0.1 --strictPort
```

如果本机尚未安装前端依赖，先用相同环境运行 `& "$nodeDir\npm.cmd" --prefix frontend ci`。使用系统安装的 Node.js 和 npm 时，可以直接在前端目录运行 `npm ci` 和 `npm run dev`。

打开 [本机活动管理后台](http://127.0.0.1:5173/)，用第一轮启动时设置的网页管理员账号登录。新建一条活动，将活动开始和报名截止时间都设在未来，保存后编辑一次，再点 **发布**。列表应从“未发布”变成“报名中”并显示发布时间。可再用状态筛选验证结果。

## 4. 验收公开报名

1. 在报名中的活动旁点 **分享报名 → 打开报名页**。复制链接到无痕窗口也能打开。
2. 用虚构姓名、学院和格式正确的手机号（如 `19900000001`）提交。网页应提示报名成功。
3. 返回管理后台，点 **刷新** 查看人数，再点 **报名名单** 查看记录。
4. 如要用手机扫码，电脑和手机连接同一网络。将前端启动命令末尾的 `--host 127.0.0.1` 改成 `--host 0.0.0.0`，在电脑浏览器用 `http://电脑局域网IPv4:5173/` 登录，再生成二维码。可用 `ipconfig` 找到电脑的 IPv4。后端继续监听 `127.0.0.1`，由 Vite 转发请求。这个测试方式适用于你信任的局域网，使用模拟报名数据。
