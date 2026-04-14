# `com.android.mydb` 微信链路分析报告

> 目标：把这套微信执行包的**整体流程链路**、**关键入口**、**数据库/文件处理逻辑**、**`message` 表读取与上传字段**、**消息类型判定规则**、**图片/语音/视频/文件的落地路径与上传动作**整理成一份便于慢慢看的文档。  
> 说明：本文基于你提供的反编译源码 `com.android.mydb`，并结合前面几个中控包（`versioncontrol / enterprisep / enterprisep_miui`）一起还原链路。个别方法因反编译不完整，文档里会明确标出“能确认”和“高概率推断”的边界。

---

## 1. 一句话结论

`com.android.mydb` 不是控制台，也不是简单的转发器；它就是**微信执行层**。  
它会直接：

1. 接收远程下发的微信补传/全量任务
2. 备份微信私有目录中的数据库和相关文件
3. 计算微信数据库密钥
4. 使用 SQLCipher 直接打开 `EnMicroMsg.db` / `SnsMicroMsg.db`
5. 解析消息、联系人、群、朋友圈、红包、文件索引等数据
6. 将数据库内容和实际文件分开上传
7. 支持主微信与分身微信双链路
8. 支持单条、按时间点、按时间段、按类型的历史补传

从职责上看，它是一套完整的：

**微信数据采集 + 本地解密 + 本地解析 + 分类上传 + 历史回溯补传** 服务包。

---

## 2. 整体架构位置

把前面几份源码拼起来，整条链路可以分为三层：

### 2.1 中控层
由服务端和企业中控包负责：

- 服务端 socket 下发命令
- `enterprisep_miui` 解析命令
- 将微信任务定向广播给 `com.android.mydb`

微信侧关键广播：

- `com.yunke.wechat.reupload.time`

也就是：

**服务端 → enterprisep_miui → com.android.mydb**

---

### 2.2 执行层
由 `com.android.mydb` 完成：

- 接收补传/全量/控制指令
- 发起数据库和文件备份
- 解密微信数据库
- 解析 `message` / `rcontact` / `chatroom` / `SnsInfo` 等表
- 把消息与文件索引落到自己的本地 provider
- 再异步上传数据库与文件

---

### 2.3 后端层
后端不是简单收整个 db，而是拆分成多类接口：

- SQLite 数据上传
- 文件校验
- 文件上传
- 登录信息上传
- 语音/VoIP 上传
- 朋友圈文件上传
- 上传状态回调

这说明它是“本地解析后结构化上传”，不是单纯整库打包上传。

---

## 3. 主要入口与总流程

---

### 3.1 入口 A：远程广播入口（历史补传 / 控制指令）

核心类：

- `com.android.mydb.receiver.BootReceiver`

关键 action：

- `com.yunke.wechat.reupload.time`

它收到 `content`（JSON）后，根据 `type` 分流：

#### 已能确认的类型

- `type = 8`：push 登录
- `type = 88`：创建设备重启标记并重启
- `type = 90`：退出并重新登录 push
- `type = 20`：删除 `/backup_wechat/0/` 和 `/backup_wechat/999/`
- `type = 10`：按 `msgSvrId` 定位单条消息补传
- `type = 11`：按 `createTime` 定位单条消息补传
- `type = 15`：补传 100 条朋友圈
- `type = 16`：补传 1000 条红包信息
- `type = 108`：把内容再广播给企业包
- `type = 120`：启动主微信全量备份
- `type = 121`：启动分身微信全量备份
- `type = 130`：启动 `remoteService`
- `type = 110`：存在一段较长逻辑，反编译不完整，但从上下文看属于历史补传/配置类流程

#### `type = 10 / 11` 的参数格式
`wxId` 字段中用 `###` 拼接：

- `wxId###msgSvrId`
- `wxId###createTime`

代码会先判断这个 `wxId` 是主微信还是分身微信，再调用：

- `NIMDBUtil.wechatReUploadByMsgSvrId(...)`
- `NIMDBUtil.wechatReUploadByCreateTime(...)`

---

### 3.2 入口 B：全量备份入口

核心类：

- `com.android.mydb.service.syncService`

关键 action：

- `STARTBACKUP`
- `UPLOAD`
- `UPLOAD-FILE`
- `STOP`
- `TYPE`

#### `STARTBACKUP`
收到后会根据 `slave` 参数分主/分身：

- `slave = 0`：主微信
- `slave = 999`：分身微信

然后依次做：

1. 解析密码（先从 `systemInfo.cfg` 拿 UIN/IMEI）
2. 推算微信账户目录（`md5("mm" + uin)`）
3. 发起 `EnMicroMsg.db` 备份
4. 备份完成后继续全量上传数据库数据
5. 再进入文件同步

---

### 3.3 入口 C：厂商备份回调入口

核心类：

- `com.android.mydb.receiver.BackupReceiver`

它负责在不同备份 `requestId` 完成后，继续推进下一步：

#### 能明确确认的关键节点

- 先拿主/分身 `systemInfo.cfg`
- 解析数据库密码
- 备份 `account.mapping`
- 备份 `EnMicroMsg.db`
- 打开 `EnMicroMsg.db`
- 更新用户信息
- 上传联系人 / 群 / 标签 / 消息 / 红包 / 图片索引
- 继续备份 `SnsMicroMsg.db`
- 打开 `SnsMicroMsg.db`
- 上传朋友圈
- 若需要则启动“前两条好友消息”补传

换句话说，`BackupReceiver` 是整条“备份结束 → 解析上传”的状态推进器。

---

## 4. 备份了哪些微信文件

核心类：

- `com.android.mydb.manager.WechatBackupManager`

确认会备份的关键文件：

### 4.1 消息库
- `EnMicroMsg.db`
- `EnMicroMsg.db-wal`
- `EnMicroMsg.db-shm`

### 4.2 朋友圈库
- `SnsMicroMsg.db`
- `SnsMicroMsg.db-wal`
- `SnsMicroMsg.db-shm`

### 4.3 账户映射文件
- `account.mapping`

### 4.4 其他散文件
在图片/视频/语音/文件处理过程中，还会按具体消息类型去备份：

- `image2/...`
- `message/media/image/...`
- `voice2/...`
- `video/...`
- `attachment/...`
- `video/.ref/d/...`
- `image2/.ref/d/...`
- 以及备份目录下的 VoIP mp3 文件

---

## 5. 数据库密码怎么来的

核心类：

- `com.android.mydb.util.WechatCfgParseUtil`

### 5.1 数据来源
它会先读取：

- `systemInfo.cfg`

其中关键字段：

- key `258` → IMEI
- key `1` → UIN

### 5.2 密码算法
算法非常明确：

1. 拼接 `IMEI + UIN`
2. 取 `MD5`
3. 取结果前 7 位

得到：

- `WECHAT_DB_PWD`
- `WECHAT_DB_PWD_SLAVE`

### 5.3 结论
它不是把密文 db 上传到后端解密，
而是：

**在本地算出 SQLCipher 密钥，再在本地打开微信数据库。**

---

## 6. 账户目录怎么定位

也是 `WechatCfgParseUtil`：

- 主微信：`md5("mm" + UIN_WECHAT)`
- 分身微信：`md5("mm" + UIN_WECHAT_SLAVE)`

这决定了它去哪个 `MicroMsg/<accountDir>/` 目录找：

- `EnMicroMsg.db`
- `SnsMicroMsg.db`
- `voice2/`
- `video/`
- `attachment/`
- `message/media/image/`

---

## 7. 它读了哪些核心数据库表

从各个 manager / util / DBUtil 的查询逻辑看，至少包括：

### 7.1 核心聊天数据
- `message`
- `rcontact`
- `rconversation`
- `chatroom`
- `chatroomInfo`
- `ContactLabel`
- `img_flag`
- `ImgInfo2`
- `WxFileIndex3`
- `WalletLuckyMoney`
- `fmessage_conversation`
- `fmessage_msginfo`
- `masssendinfo`

### 7.2 朋友圈数据
- `SnsInfo`
- `SnsComment`
- 以及 protobuf 反序列化出的 `TimeLineObject / SnsObject`

### 7.3 辅助文件
- `account.mapping`
- `systemInfo.cfg`

---

## 8. 整个流程链路（按时间顺序）

下面给你按“从收到任务到上传完成”的顺序，串成一条完整链路。

---

### 阶段 1：收到任务

来源有两种：

1. 服务端通过中控包转发广播
2. 用户/内部逻辑主动启动 `syncService`

微信历史/控制广播：

- `com.yunke.wechat.reupload.time`

处理类：

- `BootReceiver`

---

### 阶段 2：决定任务类型

根据 JSON 里的 `type` 决定：

- 单条消息补传
- 按时间补传
- 朋友圈补传
- 红包补传
- 主微信全量备份
- 分身微信全量备份
- push 登录/退出
- 启动 remoteService

---

### 阶段 3：解析微信身份

做三件事：

1. 解析 `systemInfo.cfg`
2. 算出数据库密码
3. 算出账号目录 `accountDir`

关键结果：

- `UIN_WECHAT / UIN_WECHAT_SLAVE`
- `WECHAT_DB_PWD / WECHAT_DB_PWD_SLAVE`
- `ACCOUNT_MAPPING / ACCOUNT_MAPPING_SLAVE`
- `accountDir = md5("mm" + uin)`

---

### 阶段 4：备份微信数据库和文件

通过企业能力 `MiuiAppMgrSdkUtil.backupData(...)` 导出：

- `EnMicroMsg.db`
- `SnsMicroMsg.db`
- `account.mapping`
- 各类媒体文件

并落到自己的导出目录，例如：

- `PATH_YUNKE_MicroMsg_USER_0`
- `PATH_YUNKE_MicroMsg_USER_999`
- `NEW_PATH_OUT_USER_0_MicroMsg`
- `NEW_PATH_OUT_USER_999_MicroMsg`

---

### 阶段 5：打开数据库

核心方法：

- `DBUtil.openOrCreateWechatDatabase(context, dbPath, password)`

目标库：

- `EnMicroMsg.db`（使用密码）
- `SnsMicroMsg.db`（这里看到的是 `null` 密码打开，说明它的处理方式与主聊天库不同）

---

### 阶段 6：抽取差量/全量数据

会依次处理：

- 联系人
- 群聊
- 标签
- 图片标记/索引
- 红包
- 消息
- 朋友圈
- 附件/媒体文件

这里有两套节奏：

#### 6.1 数据库记录上传
把消息/联系人等先写入自己的 provider/db，再统一上传 sqlite。

#### 6.2 真实文件上传
图片/语音/视频/文档不是直接塞进消息表，而是：

- 生成 `WxFileIndex3` 记录
- 备份对应文件到导出目录
- 校验 md5
- 调文件接口上传

---

### 阶段 7：触发历史补传

支持：

- 指定 `msgSvrId`
- 指定 `createTime`
- 指定时间段
- 指定 `wxId`
- 指定朋友圈
- 指定红包
- 指定好友前两条消息

核心方法：

- `wechatReUploadByMsgSvrId`
- `wechatReUploadByCreateTime`
- `wechatReUploadByTime`
- `wechatReUploadSNSByTime`
- `wechatReUploadRedPacket`
- `wechatReUploadByFirstTime`

---

## 9. `message` 表：它到底读了哪些字段

这一段是关键。

---

### 9.1 从原始微信 `message` 表读取的字段

在 `DBUtil` 中，多处查询 `message` 表时，确认读取了这些字段：

- `msgId`
- `msgSvrId`
- `type`
- `status`
- `isSend`
- `isShowTimer`
- `createTime`
- `talker`
- `content`
- `imgPath`
- `reserved`
- `lvbuffer`
- `transContent`
- `transBrandWording`
- `talkerId`
- `bizClientMsgId`
- `bizChatId`
- `bizChatUserId`
- `msgSeq`
- `flag`
- `solitaireFoldInfo`

有的查询是 `select * from message`，有的查询是显式列出这些字段。  
因此可以确认：**源码在消息读取阶段掌握的字段，比最终上传出去的字段更多。**

---

### 9.2 读进 `MessageModel` 的完整字段

`MessageModel` 中对应字段为：

- `msgId`
- `msgSvrId`
- `type`
- `status`
- `isSend`
- `isShowTimer`
- `createTime`
- `talker`
- `content`
- `imgPath`
- `reserved`
- `lvbuffer`
- `transContent`
- `transBrandWording`
- `talkerId`
- `bizClientMsgId`
- `bizChatId`
- `bizChatUserId`
- `msgSeq`
- `flag`
- `solitaireFoldInfo`
- `bigFileFlag`

说明它不仅看基础聊天字段，也看：

- protobuf buffer (`lvbuffer`)
- 商务/企业聊天字段 (`biz*`)
- 翻译内容 (`transContent`)
- 引用/折叠相关信息 (`solitaireFoldInfo`)
- 附加保留字段 (`reserved`)

---

### 9.3 真正写入本地上传库的消息字段

在 `DBUtil.convert2Value(MessageModel)` / `convert2ValueFromLocal(MessageModel)` 中，最终落到待上传消息表的字段是：

- `msgId`
- `msgSvrId`
- `type`
- `status`
- `imgPath`
- `isSend`
- `createTime`
- `talker`
- `content`
- `transContent`
- `talkerId`
- `flag`
- `bigFileFlag`（本地差量转上传时会带上）

也就是说：

### 读取层面掌握很多字段，
### 但真正进入上传 SQLite 的消息字段是“精选子集”。

#### 重点区别
**源库读取字段 > 本地暂存/上传字段**

这是这份代码里非常明确的设计。

---

### 9.4 `msgSvrId` 的特殊处理

在 `MessageModel.reSetAndGetMsgSvrId()` 中：

如果满足以下任一条件，会把 `msgSvrId` 改成 `createTime`：

- `type == 50`（VoIP 语音/通话类）
- `type == 64`（群 VoIP）
- `isSend == 1`（自己发送的消息）

另外 `replaceMsgSvrId(...)` 中还能看到：

- 如果 `msgSvrId` 为空/为 0，则兜底成 `P-<createTime>`

也就是说，服务端侧不能简单把所有消息都理解为“微信原始 msgSvrId”；  
这个包会在某些消息类型上改写这个字段，尤其是发送消息、VoIP 类型。

---

## 10. 它怎么区分文本 / 图片 / 文件 / 语音 / 视频

核心逻辑在：

- `MessageUtil.generateMessage(...)`

它最直接的分流依据就是：

- `messageModel.type`

### 10.1 类型映射（源码里能明确确认的）

| 微信 `message.type` | 含义 | 处理方式 |
|---|---|---|
| `1` | 文本 | 作为普通消息记录上传 |
| `3` | 图片 | 进入 `ImageMsgHandle.uploadImg(...)` |
| `34` | 语音消息 | 进入 `uploadVoice(...)` |
| `43` | 视频消息 | 进入 `uploadVideo(...)` |
| `47` | 表情/emoji | 进入 `EmojiUtil.backupEmojiMsg(...)` |
| `50` | VoIP 语音/通话消息 | 进入 `voipVoiceCheckAndUpload(...)`，并解析通话状态 |
| `64` | 群 VoIP / 群通话类 | 在 `msgSvrId` 改写逻辑中被特殊对待 |
| `1090519089` | 文件消息 | 进入 `uploadDocument(...)` |
| `10000` | 系统消息 | 作为系统类消息处理 |
| `822083633` | 引用/引用消息 | 存在 `TYPE_REF` 常量，解析逻辑未完全展开 |
| `436207665` | 红包消息 | 存在 `TYPE_LuckyMoney` 常量，红包表会单独上传 |
| `419430449` | 转账/收款类 | 存在 `TYPE_Remittance` 常量 |

> 注：`822083633 / 436207665 / 419430449` 这些值在常量中明确存在；但它们在主消息分流里并不总是作为独立文件备份入口，而是更多体现在内容/业务层解析。

---

### 10.2 文本消息怎么处理

文本消息的基础解析最简单：

- `type = 1`
- 直接取 `content`
- 通过 `parseText(MessageModel)` / `convert2Value(...)` 写入待上传表

文本类消息主要依赖数据库字段，不额外去找外部文件。

---

### 10.3 图片消息怎么处理

图片消息：

- `type = 3`

处理入口：

- `ImageMsgHandle.uploadImg(...)`

#### 它怎么定位图片文件
先查：

- `ImgInfo2`

优先按：

1. `msgSvrId`
2. 若找不到，再按 `msgId(msglocalid)`

然后推导文件路径。

#### 图片文件子类型（不是 `message.type`，而是图片文件索引里的 `msgSubType`）
源码里明确能看到这些子类型：

- `20`：RAW 原图
- `21`：小图
- `22`：高清小图
- `26`：大图 / 高清图
- `27`：PNG 大图

#### 图片路径规则
新路径规则能明确看到：

- `message/media/image/<md5(talker)>/<末4位msgSvrId>/<msgSvrId>_s`
- `..._shd`
- `..._m`

旧路径规则还能看到：

- `image2/...`
- `image2/.ref/d/...`

说明它兼容了微信图片的旧存储结构与新存储结构。

#### 图片上传流程
1. 生成 `WxFileIndex3` 记录
2. 写入本地 provider
3. 触发企业备份把源图片导出到自己的目录
4. 后续做 md5 校验和上传

---

### 10.4 语音消息怎么处理

语音消息：

- `type = 34`

处理入口：

- `MessageUtil.uploadVoice(...)`

#### 语音文件路径规则
它会根据：

- `imgPath`

先算：

- `md5(imgPath)`

再拼接成：

- `voice2/<md5前2位>/<md5第3-4位>/msg_<imgPath>.amr`

这是典型的微信语音消息落盘规则。

#### 语音处理流程
1. 生成 `WxFileIndex3` 记录
2. `msgType = 34`
3. 备份 `.amr` 文件
4. 后续走文件校验/上传链路

---

### 10.5 视频消息怎么处理

视频消息：

- `type = 43`

处理入口：

- `MessageUtil.uploadVideo(...)`

#### 视频路径规则
它直接用：

- `video/<imgPath>.mp4`

同时还会处理：

- `video/<imgPath>.mp4⌖`
- `video/<imgPath>origin.mp4`
- `video/.ref/d/...`

这说明它除了主体视频，还会尝试处理：

- ref 引用文件
- 原始 mp4
- 可能的“占位路径/索引路径”

#### 大文件判断
`isVideoBig(...)` 会从 `reserved` 里取 xml 长度信息，判断是否超过：

- `Constants.FILE_UPLOAD_LIMIT_MAX = 52428800`（50MB）

并写到：

- `bigFileFlag`

---

### 10.6 文件消息怎么处理

文件消息：

- `type = 1090519089`

处理入口：

- `MessageUtil.uploadDocument(...)`

#### 文件名来源
它会从：

- `message.content` 里的 xml

解析出：

- `title`

然后拼接为：

- `attachment/<title>`

#### 特殊情况：如果文件名是 `.mp4`
代码会把它走视频类 requestId 掩码，这说明：

**虽然 `message.type` 仍是文件消息，但若附件名以 `.mp4` 结尾，它会被当成视频附件特殊处理。**

#### 文件处理动作
1. 生成 `WxFileIndex3` 记录
2. 写入 `srcPath/outPath`
3. 若存在 ref 路径，再写 `srcRefPath/outRefPath`
4. 备份文件本体
5. 若是 mp4，还会额外备份 `origin.mp4`

---

### 10.7 表情消息怎么处理

表情消息：

- `type = 47`

处理入口：

- `EmojiUtil.backupEmojiMsg(...)`

说明它会把 emoji 当作文件类对象处理，而不是只存文本占位符。

---

### 10.8 VoIP / 通话消息怎么处理

VoIP 相关：

- `type = 50`
- 常量里还有 `TYPE_VOIP_GROUP = 64`

处理入口：

- `MessageUtil.voipVoiceCheckAndUpload(...)`
- `MessageUtil.parseVoipContent(...)`
- `call/uploadWechatCall`

#### 它做了什么
1. 从 `content` 里解析通话状态
2. 识别“通话时长 / 聊天时长 / 通话中断 / 聊天中断”
3. 推算 `startTime / endTime / duration`
4. 在 `/storage/emulated/0/backup_wechat/wechatFile` 或 `/storage/emulated/0/backup_voip` 找 mp3
5. 计算 md5
6. 上传 VoIP 录音和通话元数据

#### 特点
VoIP 这条链已经不是普通 `message` 文件上传，而是：

**消息记录 + 通话元数据 + 实际录音文件** 三段式。

---

## 11. `message` 表上传时，哪些字段最关键

如果你只关注“后端最后拿到什么”，最关键的是下面这些：

### 11.1 基础消息主键和身份
- `msgId`
- `msgSvrId`
- `type`
- `isSend`
- `createTime`
- `talker`
- `talkerId`

### 11.2 文本与业务内容
- `content`
- `transContent`
- `status`
- `flag`

### 11.3 文件定位辅助
- `imgPath`
- `bigFileFlag`

### 11.4 只在读取阶段使用、未必直接进上传 sqlite 的字段
- `reserved`
- `lvbuffer`
- `transBrandWording`
- `bizClientMsgId`
- `bizChatId`
- `bizChatUserId`
- `msgSeq`
- `solitaireFoldInfo`

这些字段虽然未全部直接写入上传表，但在解析时非常有用：

- `reserved`：视频/文件大小等 xml 信息
- `lvbuffer`：protobuf buffer，用于进一步解析
- `biz*`：企业/商务消息上下文
- `msgSeq`：消息顺序辅助
- `solitaireFoldInfo`：接龙/折叠类附加信息

---

## 12. 它不是直接上传整个 `message` 表，而是两条线并行

这个点很重要。

### 12.1 线一：数据库记录上传
把以下表打包成 SQLite 临时库上传：

- `message`
- `rcontact`
- `chatroom`
- `ContactLabel`
- `img_flag`
- `WalletLuckyMoney`
- `SnsInfo`
- `fmessage_conversation`
- `fmessage_msginfo`
- `masssendinfo`

相关接口：

- `newWechat/uploadSqlLite`
- `newWechat/uploadSqlLite/callBack`

---

### 12.2 线二：真实文件上传
文件类对象并不直接塞进 `message` 表，而是通过 `WxFileIndex3` / `FileCheckModel` 走另一条链：

- 先校验文件 md5
- 再决定是否上传
- 再做上传完成回调

相关接口：

- `wechat/checkWxFiles`
- `wechat/upfile`
- `wechat/uploadFiles`
- `wechat/uploadWechatVoiceFiles`
- `call/uploadWechatCall`
- `wechat/uploadFriendster`
- `wechatweb/wechatFriendster/check-files`
- `wechatweb/wechatFriendster/upload-files`
- `wechatweb/wechatFriendster/upload-files/call-back`

所以这套系统是：

**消息表负责“元数据”，文件链负责“真实文件”。**

---

## 13. 历史补传能力总结

它支持的补传粒度相当细：

### 13.1 单条消息补传
- 按 `msgSvrId`
- 按 `createTime`

### 13.2 时间段补传
- 按 `start/end`
- 可按 `wxId`
- 可按 `talker`

### 13.3 分类补传
- 消息
- 朋友圈
- 红包
- 好友前两条消息

### 13.4 主/分身独立补传
所有逻辑基本都对主微信和分身微信各有一套：

- `WECHAT_DB_PWD` / `WECHAT_DB_PWD_SLAVE`
- `UIN_WECHAT` / `UIN_WECHAT_SLAVE`
- `WXID_WECHAT` / `WXID_WECHAT_SLAVE`
- `PATH_YUNKE_MicroMsg_USER_0` / `PATH_YUNKE_MicroMsg_USER_999`

---

## 14. 这份包里最值得重点盯的类

如果你后面还要继续深挖，最重要的是下面这些：

### 14.1 任务入口
- `BootReceiver`
- `BackupReceiver`
- `syncService`

### 14.2 数据库核心
- `DBUtil`
- `WechatCfgParseUtil`

### 14.3 消息与文件解析
- `MessageUtil`
- `ImageMsgHandle`
- `EmojiUtil`
- `UploadUtil`
- `WxFileIndexUploadManager`

### 14.4 历史补传
- `NIMDBUtil`

### 14.5 各业务模块上传
- `MessageUploadManager`
- `RContactsUploadManager`
- `ChatroomUploadManager`
- `ContactLabelUploadManager`
- `LuckyMoneyUploadManager`
- `SnsUploadManager`
- `RecordManager`

---

## 15. 关键结论（给你快速回看）

### 15.1 这包是不是直接读微信数据库？
是。  
它本地算密码，然后直接用 SQLCipher 打开 `EnMicroMsg.db`。

### 15.2 `message` 表它读了哪些字段？
至少读了：

- `msgId`
- `msgSvrId`
- `type`
- `status`
- `isSend`
- `isShowTimer`
- `createTime`
- `talker`
- `content`
- `imgPath`
- `reserved`
- `lvbuffer`
- `transContent`
- `transBrandWording`
- `talkerId`
- `bizClientMsgId`
- `bizChatId`
- `bizChatUserId`
- `msgSeq`
- `flag`
- `solitaireFoldInfo`

### 15.3 最终真正进上传消息表的字段有哪些？
核心是：

- `msgId`
- `msgSvrId`
- `type`
- `status`
- `imgPath`
- `isSend`
- `createTime`
- `talker`
- `content`
- `transContent`
- `talkerId`
- `flag`
- `bigFileFlag`

### 15.4 怎么区分文本/图片/文件/语音/视频？
主要看 `message.type`：

- `1` 文本
- `3` 图片
- `34` 语音
- `43` 视频
- `47` 表情
- `50` VoIP
- `1090519089` 文件

### 15.5 文件是怎么拿的？
不是从 `message.content` 直接上传，而是：

- 根据 `imgPath` / xml / `ImgInfo2` / `msgSvrId` / `talker`
- 推导微信私有目录下真实文件路径
- 走备份导出
- 做 md5 校验
- 走独立文件接口上传

### 15.6 是上传整库还是本地解析后上传？
是**本地解析后分模块上传**。  
数据库记录和真实文件是两条不同链路。

---

## 16. 还存在的边界与不确定点

下面这些点目前不能 100% 下死结论，但方向已经很清楚：

1. `DBUtil.openOrCreateWechatDatabase(...)` 反编译不完整，但从调用关系和返回对象已能确认它确实在打开 SQLCipher 微信库。
2. `BootReceiver` 中 `type = 110` 的长逻辑没有完整展开，但从上下文看仍属于历史补传/配置下发链路。
3. `ImageMsgHandle.findImage(...)` 某些旧路径兼容细节非常长，虽然大方向已清楚，但旧版微信不同图片命名分支还可以继续细抠。
4. `TYPE_REF / TYPE_LuckyMoney / TYPE_Remittance` 的主消息内容解析还可以再继续深挖，尤其是 XML 里的字段细节。

---

## 17. 你接下来最值得继续看的三个点

### 17.1 细抠 `message.content` XML 解析
重点看：

- 文件消息 XML
- 引用消息 XML
- 转账/红包 XML
- 视频 `reserved` XML

这会让你更清楚服务端到底能拿到哪些正文细节。

### 17.2 细抠 `NIMDBUtil` 的补传库生成逻辑
重点看：

- 临时 SQLite 是怎么建表的
- 哪些字段会被丢掉
- 哪些字段会保留
- 历史补传与实时上传的差异

### 17.3 细抠 `UploadUtil / PostStringCallback / CheckFilesCallback`
重点看：

- 文件先校验再上传的具体协议
- OSS / HTTP 两套上传路径怎么切换
- 失败重试与回调逻辑

---

## 18. 附：源码中最关键的文件（便于你自己回看）

- `com/android/mydb/receiver/BootReceiver.java`
- `com/android/mydb/receiver/BackupReceiver.java`
- `com/android/mydb/service/syncService.java`
- `com/android/mydb/util/WechatCfgParseUtil.java`
- `com/android/mydb/database/DBUtil.java`
- `com/android/mydb/util/MessageUtil.java`
- `com/android/mydb/util/ImageMsgHandle.java`
- `com/android/mydb/manager/WechatBackupManager.java`
- `com/android/mydb/manager/MessageUploadManager.java`
- `com/android/mydb/netease/NIMDBUtil.java`
- `com/android/mydb/util/UploadUtil.java`
- `com/android/mydb/util/URLConstant.java`

---

如果你后面还要继续细抠，我最建议下一版就专门写两份：

1. **`message.type` 全类型说明表**（把红包、转账、引用、系统消息、群消息、企微相关特殊项全部列全）
2. **`message.content / reserved / lvbuffer` 解析文档**（这个最接近“它到底上传了哪些消息正文细节”）
