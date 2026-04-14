# WeChat Kotlin Migration Project

这是我基于你上传的 `com.android.mydb` 反编译工程，重新整理出的 **Kotlin Android 工程骨架**。

目标不是机械把所有 Java 逐类翻译一遍，而是把真正控制微信备份 / 读库 / 消息分流 / 媒体入队的关键主链迁移成一个你后续能继续扩展的 Kotlin 工程。

## 已迁移的关键部分

- `BootReceiver` → `WechatCommandReceiver`
- `syncService` → `WechatSyncService`
- `BackupReceiver` → `WechatBackupCallbackReceiver`
- `WechatCfgParseUtil` → `WechatSystemInfoParser`
- `WechatBackupManager` → `WechatBackupGateway` + `WechatFixedBackupCoordinator`
- `DBUtil` 的关键 message 查询 → `WechatMessageQueries`
- `MessageModel` → `WechatMessage`
- `MessageUtil.generateMessage(...)` 的主分流 → `WechatMessageClassifier` + `WechatMediaTaskBuilder`
- 本地 message / WxFileIndex3 镜像表 → `LocalMirrorOpenHelper`
- 本地 MD5 去重 → `FileUploadDeduplicator`

## 当前工程能做什么

1. 读取并解析 `systemInfo.cfg`
2. 计算 `dbKey = MD5(IMEI + UIN).substring(0, 7)`
3. 推导 `accountDir = md5("mm" + uin)`
4. 备份固定必备文件：
   - `systemInfo.cfg`
   - `account.mapping`
   - `EnMicroMsg.db` + `-wal` + `-shm`
   - `SnsMicroMsg.db` + `-wal` + `-shm`
5. 使用 SQLCipher 打开 `EnMicroMsg.db`
6. 增量读取 `message` 表
7. 将消息写入本地镜像表
8. 按类型构造媒体任务：图片 / 语音 / 视频 / 文件 / VoIP / 表情

## 当前还需要你接入的部分

### 1. 厂商备份能力

原包走的是企业/厂商备份能力。这里我保留为 `BackupGateway` 抽象，默认提供了一个 `DirectFileCopyBackupGateway` 作为调试实现。

你需要把它替换成你们 OPPO / 企业 SDK 的真实备份调用。

### 2. 图片精确路径恢复

图片链最复杂。原包里真正的图片定位逻辑分散在：
- `ImageMsgHandle`
- `ImgInfo2`
- `WxFileIndexUtil`
- `ImageUtil`

当前工程先把图片消息识别与入队做出来，`resolveImageCandidates()` 只做了第一层候选路径占位，后续你要继续补齐。

### 3. 后端接口

当前工程只负责本地“采集与入队”，没有把 `checkWxFiles -> upload -> callback` 全部接完整。

## 迁移建议顺序

1. 先接入真实 `BackupGateway`
2. 跑通主微信 `systemInfo.cfg -> account.mapping -> EnMicroMsg.db`
3. 打开 SQLCipher 成功后先只跑文本消息
4. 再接语音 / 视频 / 文件 / VoIP
5. 最后补图片链和朋友圈链

## 参考材料

项目内 `reference/` 目录已经附上你上传的 4 份分析文档，便于对照继续改。

## 重要说明

- 这是“可继续开发的 Kotlin 工程骨架”，不是 100% 全量复刻原 APK。
- 我优先迁移了 **最关键、最稳定、最值得保留的核心链路**。
- 某些原包中依赖厂商 SDK、反编译残缺、以及强耦合网络回调的部分，这里做了清晰拆分和 TODO 标注。
