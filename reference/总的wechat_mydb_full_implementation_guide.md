# `com.android.mydb` 微信执行链路总文档（面向迁移到你的 App / 交给 Codex 实现）

> 目标：把 `com.android.mydb` 的**完整核心逻辑**整理成一份可以直接交给 Codex 或开发同学落地的实现说明。  
> 重点不是“源码逐类介绍”，而是：**这套东西整体怎么跑、关键数据从哪里来、怎么区分消息类型、怎么上传、怎么补传、迁移时你需要实现哪些模块。**

---

# 1. 一句话结论

`com.android.mydb` 是这套系统里专门负责 **微信数据执行** 的服务包，不是控制台，也不是简单转发器。它在本地完成：

1. 接收远程补传/全量同步命令
2. 触发微信私有目录备份
3. 解析 `systemInfo.cfg` 和 `account.mapping`
4. 推导微信数据库密码
5. 打开 `EnMicroMsg.db` / `SnsMicroMsg.db`
6. 读取消息、联系人、群、朋友圈、红包、文件索引等数据
7. 结构化上传消息元数据
8. 单独上传图片/语音/视频/文件/VoIP/朋友圈媒体
9. 支持主微信和分身微信
10. 支持按消息、按时间点、按时间段、按类别的历史补传

如果你要把它“搬进自己的 App”，那你真正需要复刻的不是某一个类，而是下面这 6 个核心能力：

- 任务接收与调度
- 微信文件备份
- DB 密码推导与 SQLCipher 读库
- 消息表解析与消息类型分流
- 媒体文件索引 / 校验 / 上传
- 增量同步与历史补传

---

# 2. 整体架构位置

这套系统至少分三层：

## 2.1 中控层

负责接收后端命令并转发给微信执行包：

- 服务端 socket 下发命令
- `enterprisep_miui` 解析命令
- 向 `com.android.mydb` 发定向广播

微信侧关键广播：

- `com.yunke.wechat.reupload.time`

所以微信任务入口链路是：

**服务端 → 企业中控包 → `com.android.mydb`**

## 2.2 微信执行层

由 `com.android.mydb` 完成：

- 收任务
- 备份微信数据库和文件
- 解析账号与数据库密钥
- 打开 SQLCipher 数据库
- 读取消息与媒体索引
- 本地落待上传数据
- 分类调用后端接口上传

## 2.3 后端接口层

后端不是收一整个微信库，而是拆成多类接口：

- 消息 / 联系人 / 群 / 红包 / 朋友圈等 SQLite 数据上传
- 文件存在性校验
- 普通媒体文件上传
- 朋友圈媒体上传
- 语音 / VoIP 上传
- 上传成功回调

也就是说，这套架构不是“导库上传”，而是：

**本地解密 → 本地解析 → 分类上传**

---

# 3. 建议你在自己 App 里按什么模块拆

如果你准备叫 Codex 实现，建议直接按下面模块拆，不要照搬原包名。

## 3.1 `WechatTaskDispatcher`

职责：

- 接收外部命令
- 解析任务 JSON
- 分发到不同执行分支

输入：

- 广播 / websocket / 本地调试命令

输出：

- 全量同步任务
- 增量同步任务
- 单条消息补传任务
- 时间段补传任务
- 特定类型补传任务（朋友圈 / 红包 / 媒体）

## 3.2 `WechatBackupEngine`

职责：

- 通过企业能力或其他可用能力备份微信私有目录文件
- 备份主微信与分身微信两套数据

关键文件：

- `EnMicroMsg.db`
- `EnMicroMsg.db-wal`
- `EnMicroMsg.db-shm`
- `SnsMicroMsg.db`
- `SnsMicroMsg.db-wal`
- `SnsMicroMsg.db-shm`
- `account.mapping`
- `systemInfo.cfg`
- 后续按消息路径备份图片/语音/视频/附件

## 3.3 `WechatDbKeyResolver`

职责：

- 读取 `systemInfo.cfg`
- 提取 `IMEI` 和 `uin`
- 计算微信 SQLCipher 密钥

源码里逻辑是：

- `MD5(IMEI + UIN)`
- 取前 7 位作为数据库密码

需要分别维护：

- 主微信密码
- 分身微信密码

## 3.4 `WechatDatabaseReader`

职责：

- 用 SQLCipher 打开 `EnMicroMsg.db`
- 读取 `message`、`rcontact`、`chatroom`、`WxFileIndex3` 等表
- 用普通 SQLite 打开/处理本地缓存库

## 3.5 `WechatMessagePipeline`

职责：

- 从微信原始 `message` 表读消息
- 规整成你自己的消息模型
- 根据 `type + content + imgPath + reserved + lvbuffer` 判断消息类型
- 文本类直接入待上传表
- 媒体类同时推入媒体上传队列

## 3.6 `WechatMediaPipeline`

职责：

- 管理图片/语音/视频/文件/VoIP/朋友圈媒体
- 根据消息内容和文件索引推导原始路径
- 触发备份导出
- 计算 MD5
- 先查后端是否已存在
- 若不存在则上传
- 上传成功后清理本地待上传队列

## 3.7 `WechatSyncStateStore`

职责：

- 存同步游标
- 存主/分身状态
- 存补传时间窗
- 存最近处理到的 `msgId` / `createTime`
- 存文件去重状态

## 3.8 `WechatUploadApi`

职责：

- 封装消息元数据上传
- 文件 check 接口
- 文件 upload 接口
- callback 接口
- 朋友圈专用接口
- 登录日志接口
- VoIP 接口

---

# 4. 任务入口和总流程

`com.android.mydb` 主要有 3 个入口。

---

## 4.1 入口 A：远程广播入口

核心类：

- `BootReceiver`

关键 action：

- `com.yunke.wechat.reupload.time`

收到后会取出 `content` JSON，然后根据 `type` 分流。

### 已能确认的关键 `type`

- `type = 8`：push 登录
- `type = 88`：创建设备重启标记并重启
- `type = 90`：退出并重登 push
- `type = 20`：删除本地备份目录
- `type = 10`：按 `msgSvrId` 补传单条消息
- `type = 11`：按 `createTime` 补传单条消息
- `type = 15`：补传 100 条朋友圈
- `type = 16`：补传 1000 条红包信息
- `type = 108`：把内容转广播给企业包
- `type = 120`：主微信全量备份
- `type = 121`：分身微信全量备份
- `type = 130`：启动 `remoteService`
- `type = 110`：历史补传/配置类逻辑，反编译不完整，但属于补传主链

### 单条补传参数格式

在 `type = 10 / 11` 下，`wxId` 中会用 `###` 拼：

- `wxId###msgSvrId`
- `wxId###createTime`

代码会先判断这个 `wxId` 属于主微信还是分身微信，再调用对应补传方法。

---

## 4.2 入口 B：全量备份入口

核心类：

- `syncService`

关键 action：

- `STARTBACKUP`
- `UPLOAD`
- `UPLOAD-FILE`
- `STOP`

### `STARTBACKUP` 主流程

收到后根据 `slave` 参数判断：

- `slave = 0`：主微信
- `slave = 999`：分身微信

然后推进：

1. 读取 `systemInfo.cfg`
2. 解析 UIN / IMEI / wxId
3. 计算数据库密码
4. 推导微信账号目录
5. 备份 `account.mapping`
6. 备份 `EnMicroMsg.db`
7. 备份完成后上传消息库数据
8. 继续备份 `SnsMicroMsg.db`
9. 上传朋友圈数据
10. 再进入文件类同步

---

## 4.3 入口 C：备份完成回调入口

核心类：

- `BackupReceiver`

它是整条状态推进链的“回调推进器”。

### 它做的事情

根据不同 `requestId`：

- 处理主/分身微信不同阶段的备份结果
- 解析数据库密码
- 继续下一批文件备份
- 备份完消息库后立即打开 DB
- 触发联系人 / 群 / 标签 / 红包 / 消息 / 图片索引上传
- 备份完朋友圈库后上传朋友圈
- 需要时触发“前两条好友消息”补传

一句话说：

**BootReceiver 决定干什么，syncService 决定怎么备份，BackupReceiver 决定什么时候推进下一步。**

---

# 5. 微信备份了哪些关键文件

核心类：

- `WechatBackupManager`

## 5.1 主消息库

- `EnMicroMsg.db`
- `EnMicroMsg.db-wal`
- `EnMicroMsg.db-shm`

## 5.2 朋友圈库

- `SnsMicroMsg.db`
- `SnsMicroMsg.db-wal`
- `SnsMicroMsg.db-shm`

## 5.3 账号辅助文件

- `account.mapping`
- `systemInfo.cfg`

## 5.4 媒体文件

按消息类型进一步备份：

- `image2/...`
- `message/media/image/...`
- `voice2/...`
- `video/...`
- `attachment/...`
- `video/.ref/d/...`
- `image2/.ref/d/...`
- VoIP 备份目录下的 mp3

这些文件不是一开始全备，而是**解析消息后按需备份导出**。

---

# 6. 数据库密码如何推导

核心类：

- `WechatCfgParseUtil`

## 6.1 数据来源

从 `systemInfo.cfg` 里取：

- `IMEI`
- `uin`

## 6.2 算法

源码逻辑可以还原为：

- `dbKey = MD5(IMEI + UIN).substring(0, 7)`

分别维护：

- `WECHAT_DB_PWD`
- `WECHAT_DB_PWD_SLAVE`

## 6.3 账号目录推导

微信数据库所在目录并不是明文 UIN，而是账号目录 hash。源码里有：

- `md5("mm" + uin)`

通常会用它拼出微信账号目录，然后定位：

- `MicroMsg/<accountDir>/EnMicroMsg.db`
- `MicroMsg/<accountDir>/SnsMicroMsg.db`

---

# 7. 数据库打开方式

核心类：

- `DBUtil`
- `DecodeUtil`

## 7.1 微信数据库

使用：

- `net.sqlcipher.database.SQLiteDatabase`

打开：

- `EnMicroMsg.db`
- `SnsMicroMsg.db`

## 7.2 本地缓存库

程序自己会维护本地表，用来保存待上传消息、待上传媒体索引、emoji、本地游标等状态。

换句话说：

- **微信原始库**：只读 / 解析源
- **本地库**：缓存待上传数据和同步状态

---

# 8. 这套程序到底从微信 `message` 表里读了哪些字段

核心位置：

- `DBUtil`
- `MessageModel`

## 8.1 原始 SQL 读取字段

核心查询字段如下：

```sql
select 
  msgId,
  msgSvrId,
  type,
  status,
  isSend,
  isShowTimer,
  createTime,
  talker,
  content,
  imgPath,
  reserved,
  lvbuffer,
  transContent,
  transBrandWording,
  talkerId,
  bizClientMsgId,
  bizChatId,
  bizChatUserId,
  msgSeq,
  flag,
  solitaireFoldInfo
from message
```

## 8.2 代码里明确持有的消息字段

`MessageModel` 中可见字段：

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

## 8.3 最终保存到本地待上传 `message` 表的字段

本地表字段是：

- `msgId`
- `msgSvrId`
- `type`
- `status`
- `isSend`
- `createTime`
- `talker`
- `content`
- `transContent`
- `talkerId`
- `flag`
- `imgPath`
- `bigFileFlag`

也就是说，源码做的是：

**先从微信 `message` 表读“较完整字段集”，再裁剪成自己的待上传消息表。**

## 8.4 只参与解析但默认不进本地消息表的字段

这些字段会被读取，但默认不直接进入最终待上传表：

- `reserved`
- `lvbuffer`
- `transBrandWording`
- `bizClientMsgId`
- `bizChatId`
- `bizChatUserId`
- `msgSeq`
- `solitaireFoldInfo`
- `isShowTimer`

它们主要用来：

- 解析特殊消息内容
- 推断媒体信息
- 处理企业/业务会话字段
- 处理 VoIP 文案或录音线索

---

# 9. `msgSvrId` 的修正规则

核心逻辑：

- `MessageModel.reSetAndGetMsgSvrId()`
- `MessageModel.replaceMsgSvrId(...)`

## 9.1 发送方消息会修正 `msgSvrId`

满足以下任一条件时，会把 `msgSvrId` 替换成 `createTime`：

- `type == 50`（VoIP 类）
- `type == 64`
- `isSend == 1`

## 9.2 空 `msgSvrId` 的兜底

如果原始 `msgSvrId` 是空或 `0`，会兜底为：

- `P-<createTime>`

这说明系统很依赖 `msgSvrId` 做：

- 去重
- 文件回溯
- 上传完成后删除待上传数据

迁移时你也应统一做一个“消息唯一键”生成器，避免 `msgSvrId` 不稳定导致补传和文件清理失败。

---

# 10. 怎么区分文本 / 图片 / 文件 / 语音 / 视频 / VoIP

它不是只看一个字段，而是：

**`message.type + content + imgPath + reserved + lvbuffer + 文件索引表` 联合判断。**

下面是源码里能直接坐实的关键类型。

## 10.1 文本消息

- `type = 1`

处理方式：

- 只做结构化消息上传
- 不额外触发媒体备份

依赖字段：

- `content`
- `talker`
- `createTime`
- `msgSvrId`

## 10.2 图片消息

- `type = 3`

处理方式：

- 先上传消息元数据
- 再调用图片处理链路
- 通过 `imgPath` 和图片索引路径找到真实图片文件

典型处理方法：

- `ImageMsgHandle.uploadImg(...)`
- `UploadUtil.uploadImage(...)`

图片区分时不只一个文件，通常会拆成不同子类型，例如：

- 小图
- 高清小图
- 高清图

本地媒体队列表中的典型 `msgSubType`：

- `21`
- `22`
- `26`

## 10.3 语音消息

- `type = 34`

处理方式：

- 先上传消息元数据
- 再按语音路径单独上传文件

注意：

- 微信消息类型的 `34` 是消息类型
- 进入文件上传链路后，对应 `FileCheckModel.type = 4`

也就是说消息类型编码和文件上传编码不是一个维度。

## 10.4 视频消息

- `type = 43`

处理方式：

- 先上传消息元数据
- 再按 `video/<imgPath>.mp4` 组织视频文件上传
- 同时可能处理一个 `ref` 引用文件

典型路径：

- `video/<imgPath>.mp4`
- `video/.ref/d/...`

## 10.5 表情消息

- `type = 47`

处理方式：

- 不是走普通 `WxFileIndex3_local` 删除逻辑
- 有独立 `Emoji_local` / `EmojiSlave_local` 队列

说明源码里把表情做成了独立上传链。

## 10.6 VoIP / 通话提示类消息

- `type = 50`

处理方式：

- 消息元数据照常入本地消息表
- 如果 `content` 里存在文件名 / 录音 JSON 线索，再单独走 VoIP 文件链路

VoIP 文件上传不是普通图片/语音上传，而是：

- 从 `message.content` 提取 `fileName`
- 到备份目录找真实 mp3
- 构造 `FileCheckModel.type = 5`
- 走 `call/uploadWechatCall` 或对应上传链

并且这个链路里常把 `createTime` 当文件侧唯一键，而不是严格依赖微信原始 `msgSvrId`。

## 10.7 文件/附件消息

源码里文件消息不是靠一个单纯常量就结束，而是结合：

- `type`
- XML `content`
- `title`
- `imgPath`
- `attachment/...` 路径

处理方式：

- 从 XML `content` 提取文件名 `title`
- 拼出 `attachment/<title>`
- 生成 `srcPath/outPath`
- 必要时还生成 `srcRefPath/outRefPath`
- 再进入文件 check / upload 链路

## 10.8 超大文件 / 特殊业务消息

源码里还有：

- `bigFileFlag`
- `reserved`
- `lvbuffer`
- `bizClientMsgId`
- `bizChatId`
- `bizChatUserId`

这些字段说明它还会处理：

- 大文件场景
- 企业/业务会话
- 特殊富媒体/业务消息

不过从现有反编译结果，最稳妥的迁移方式仍然是：

- 先把原始字段完整读出来
- 先支持文本/图片/语音/视频/文件/VoIP 这 6 条主链
- 后续再迭代特殊业务消息

---

# 11. 真实媒体文件是怎么找到的

媒体文件不是直接存数据库里，它是通过多种线索推导出来。

## 11.1 主要来源

- `message.imgPath`
- `message.content` 中 XML / JSON 字段
- `reserved`
- `lvbuffer`
- `WxFileIndex3`
- 朋友圈对象里的媒体列表

## 11.2 主要原始目录

源码中能确认的关键目录：

- `image2/...`
- `message/media/image/...`
- `voice2/...`
- `video/...`
- `attachment/...`
- `video/.ref/d/...`
- `image2/.ref/d/...`

## 11.3 路径字段含义

### `srcPath`

微信私有目录里的原始路径。

### `outPath`

通过企业备份能力导出到外部后，实际可算 MD5、可上传的路径。

### `srcRefPath`

微信私有目录里的引用文件路径。

### `outRefPath`

引用文件导出到外部后的路径。

你迁移时建议自己的媒体任务模型也保留这四个字段，后续定位问题会方便很多。

---

# 12. `WxFileIndex3` 在整条媒体链中的作用

这张表非常关键，本质是**媒体上传任务队列表**。

## 12.1 表中典型字段

- `msgId`
- `msgSvrId`
- `isSend`
- `username`
- `msgType`
- `msgSubType`
- `webpPath`
- `srcPath`
- `outPath`
- `srcRefPath`
- `outRefPath`
- `size`
- `md5`
- `msgtime`
- `requestId`

## 12.2 它干了什么

- 保存这条媒体属于哪条消息
- 保存原始文件和导出文件路径
- 保存文件分类和子类型
- 保存备份请求 ID
- 保存导出后的 MD5
- 供后续 check / upload / cleanup 使用

换句话说：

**消息解析层和真正文件上传层之间，就是靠 `WxFileIndex3` 接起来的。**

---

# 13. 媒体文件是怎么进入上传队列的

## 13.1 图片

- 命中 `type = 3`
- 根据 `imgPath` 和图片索引生成任务
- 写入 `WxFileIndex3_local` 或分身表
- 插入前会查重，避免同一消息同一路径重复入队

## 13.2 视频

- 命中 `type = 43`
- 拼：`video/<imgPath>.mp4`
- 可能同时生成 `ref` 文件路径
- 写入媒体队列表

## 13.3 文件/附件

- 从 XML `content` 里取 `title`
- 拼：`attachment/<title>`
- 必要时同时生成 `ref` 路径
- 写入媒体队列表

## 13.4 普通语音

- 命中 `type = 34`
- 独立走语音文件上传链
- 在文件上传维度对应 `FileCheckModel.type = 4`

## 13.5 表情

- 命中 `type = 47`
- 走 `Emoji_local` / `EmojiSlave_local`
- 不是普通 `WxFileIndex3_local` 清理路径

## 13.6 VoIP

- 命中 `type = 50`
- 从 `content` 的 JSON 提取 `fileName`
- 去这些目录找：
  - `/storage/emulated/0/backup_wechat/wechatFile/`
  - `/storage/emulated/0/backup_voip/`
- 找到后算 MD5
- 构造 `FileCheckModel.type = 5`
- 走 VoIP 上传链

## 13.7 朋友圈媒体

完全单独处理，不走普通 `wechat/checkWxFiles`。

会先从朋友圈对象组装：

- 图片：`snst_<id>.jpg`
- 视频：`sight_<id>.mp4`

然后走朋友圈专用 check / upload / callback 接口。

---

# 14. 上传不是直接发文件，而是“先 check 再 upload”

这是整条媒体上传链最关键的一点。

## 14.1 先算 MD5

导出文件之后，本地会先算文件 MD5。

## 14.2 再调 check 接口

普通微信媒体会走：

- `wechat/checkWxFiles`

朋友圈媒体会走：

- `wechatweb/wechatFriendster/check-files`

目的：

- 先问后端“这个文件你是不是已经有了”

## 14.3 后端说没有，才真正上传

普通媒体可能走：

- `wechat/upfile`
- 或 OSS 上传 + `wechat/callback/upfile`

朋友圈媒体会走朋友圈专用接口。

## 14.4 后端说已有，则直接回调清理

如果 check 返回文件已存在，本地通常不会重复上传，而是：

- 直接删本地待上传记录
- 标记该 MD5 已处理
- 清理临时备份文件

这套逻辑的本质就是：

**服务端去重 + 本地去重 双重保护。**

---

# 15. 本地怎么做去重

## 15.1 队列入库查重

插入 `WxFileIndex3_local` 前会查：

- `msgId + msgType + msgSubType + srcPath`

避免同一媒体任务重复入队。

## 15.2 MD5 级别去重

本地会维护上传状态，例如：

- 正在上传集合
- 已上传记录
- `uploaded.txt` 一类的本地已完成标记

所以同一文件即便被多次扫到，也能通过 MD5 拦住重复上传。

## 15.3 服务端二次去重

通过 `checkWxFiles` 再确认后端是否已有。

---

# 16. 上传成功 / 失败后怎么处理

## 16.1 成功后

会做下面几件事：

- 删除本地待上传索引记录
- 记录该 MD5 已成功上传
- 从“上传中”状态移除
- 删除或移动备份导出文件
- 某些场景触发 callback 告诉后端上传完成

## 16.2 失败后

一般会：

- 从“上传中”状态移除
- 某些路径下删除队列表项
- 某些路径下保留等待后续重试
- Wi-Fi 环境与移动网络下策略可能不同

## 16.3 一个边界说明

`UploadUtil.uploadFile()` 反编译不完整，所以“最终每个分支的所有 if 条件”不能 100% 逐行复原；但从调用链能确定：

- 先校验
- 再上传
- 成功后清理
- 失败后释放占用并按条件重试/放弃

这部分对你迁移实现已经够用了。

---

# 17. 除了消息，还有哪些数据会上传

`com.android.mydb` 不只传聊天消息。

## 17.1 联系人

相关管理器：

- `RContactsUploadManager`
- `ContactLabelUploadManager`

说明会上传：

- 好友/联系人基础信息
- 联系人标签

## 17.2 群与群成员

相关管理器：

- `ChatroomUploadManager`

说明会上传：

- 群基础信息
- 群成员关系

## 17.3 朋友圈

相关管理器：

- `SnsUploadManager`

说明会上传：

- 朋友圈结构化数据
- 朋友圈图片 / 视频

## 17.4 红包

相关管理器：

- `LuckyMoneyUploadManager`

说明会上传：

- 红包相关记录
- 并支持历史红包补传

## 17.5 登录日志

存在：

- `wechat/saveWeChatLoginLog`

说明会单独上传微信登录相关信息。

## 17.6 语音 / VoIP

存在：

- `wechat/uploadWechatVoiceFiles`
- `call/uploadWechatCall`

说明普通语音和 VoIP 是两条不同上传链。

---

# 18. 增量同步怎么做

`com.android.mydb` 不是每次都全量重扫，它有游标逻辑。

## 18.1 增量查询方式

对微信原始 `message` 表常见查询方式：

- `msgId > oldMsgId`
- `createTime > lastCreateTime`
- `createTime between ? and ?`
- `msgSvrId = ?`
- `ORDER BY msgId DESC limit 1`
- `ORDER BY createTime DESC limit 1`

## 18.2 你迁移时建议至少保存这些同步状态

- 主微信最近同步 `msgId`
- 主微信最近同步 `createTime`
- 分身微信最近同步 `msgId`
- 分身微信最近同步 `createTime`
- 朋友圈最近同步位置
- 红包最近同步位置
- 最近补传时间窗
- 最近一次全量时间

---

# 19. 历史补传怎么做

这是这套系统非常关键的能力。

## 19.1 单条消息补传

### 按 `msgSvrId`

- `type = 10`
- 调 `wechatReUploadByMsgSvrId(...)`

### 按 `createTime`

- `type = 11`
- 调 `wechatReUploadByCreateTime(...)`

## 19.2 按时间段补传

会把补传时间窗存到本地配置，再由后续周期任务去跑。

常见做法：

- `createTime between start and end`
- 按批扫消息表
- 文本与媒体分流上传

## 19.3 按类别补传

- `type = 15`：补传朋友圈
- `type = 16`：补传红包

## 19.4 全量重跑

- `type = 120`：主微信全量
- `type = 121`：分身微信全量

---

# 20. 主微信和分身微信是怎么区分的

源码明显支持双链路。

## 20.1 标识

- `slave = 0`：主微信
- `slave = 999`：分身微信

## 20.2 分别维护的数据

- `WECHAT_DB_PWD` / `WECHAT_DB_PWD_SLAVE`
- `UIN_WECHAT` / `UIN_WECHAT_SLAVE`
- `WXID_WECHAT` / `WXID_WECHAT_SLAVE`
- `EnMicroMsg_new_User0` / `EnMicroMsg_new_User999`
- `SnsMicroMsg_new_User0` / `SnsMicroMsg_new_User999`

## 20.3 迁移建议

不要把主微信和分身微信混在一套状态里，建议把所有状态都按 `accountSlot` 维度隔离：

- `MAIN`
- `CLONE`

包括：

- DB key
- wxId
- UIN
- 游标
- 待上传消息表
- 待上传媒体表
- 文件去重状态

---

# 21. 迁移到你的 App 时，最值得保留的核心逻辑

如果你的目标不是 1:1 完全复刻所有历史代码，而是做一套可控、可维护、可交给 Codex 实现的版本，我建议保留以下核心逻辑。

## 21.1 必保留

### A. 任务模型

统一抽象成：

- `FULL_SYNC`
- `INCREMENTAL_SYNC`
- `REUPLOAD_BY_MSGSVRID`
- `REUPLOAD_BY_CREATETIME`
- `REUPLOAD_BY_TIMERANGE`
- `REUPLOAD_SNS`
- `REUPLOAD_LUCKYMONEY`
- `UPLOAD_MEDIA_ONLY`

### B. 账号模型

统一维护：

- `slot`（主 / 分身）
- `uin`
- `wxId`
- `dbKey`
- `accountDir`

### C. 原始消息模型

至少保留：

- `msgId`
- `msgSvrId`
- `type`
- `status`
- `isSend`
- `createTime`
- `talker`
- `content`
- `imgPath`
- `reserved`
- `lvbuffer`
- `transContent`
- `talkerId`
- `flag`
- `bigFileFlag`

### D. 本地上传消息模型

至少保留：

- `msgId`
- `msgSvrId`
- `type`
- `status`
- `isSend`
- `createTime`
- `talker`
- `content`
- `transContent`
- `talkerId`
- `flag`
- `imgPath`
- `bigFileFlag`

### E. 媒体任务模型

至少保留：

- `msgId`
- `msgSvrId`
- `msgType`
- `msgSubType`
- `srcPath`
- `outPath`
- `srcRefPath`
- `outRefPath`
- `md5`
- `requestId`
- `slot`

### F. 同步状态模型

至少保留：

- 最近 `msgId`
- 最近 `createTime`
- 补传开始时间
- 补传结束时间
- 上次全量时间
- 文件是否正在上传
- 文件 MD5 是否已上传

## 21.2 强烈建议优化的点

### A. 不要把所有流程硬塞在 BroadcastReceiver

原工程里有不少流程直接从 Receiver 拉长链条推进。你自己做时建议改成：

- Receiver / websocket 只负责收任务
- 真正执行放到前台 Service 或 WorkManager + 前台保活里

### B. 不要把“消息类型判断”和“文件上传”耦死

建议拆两步：

1. `classifyMessage(message)`
2. `buildMediaTasks(message, classification)`

### C. 一定要有统一唯一键策略

建议统一函数：

- `stableMsgKey = normalized(msgSvrId, createTime, isSend, type)`

避免发送方消息、VoIP、空 `msgSvrId` 让去重逻辑乱掉。

### D. 媒体上传必须先 check 再 upload

否则：

- 服务器压力会暴涨
- 本地重复上传会非常严重

### E. 主 / 分身隔离必须从模型层做

不要靠字符串后缀临时拼。

---

# 22. 给 Codex 的实现优先级建议

如果你后面要让 Codex 帮你落实现，我建议按下面顺序做。

## 第一阶段：打通最小闭环

1. 任务接收
2. 读取 `systemInfo.cfg`
3. 算 DB key
4. 打开 `EnMicroMsg.db`
5. 增量读 `message`
6. 文本消息上传
7. 把图片/语音/视频/文件任务落本地媒体表

## 第二阶段：媒体闭环

8. 按消息类型推导 `srcPath`
9. 备份导出媒体文件
10. 计算 MD5
11. `checkWxFiles`
12. 上传普通媒体
13. 成功后清理本地队列

## 第三阶段：补齐数据域

14. 联系人上传
15. 群 / 群成员上传
16. 红包上传
17. 朋友圈 DB 读取与上传
18. 朋友圈媒体上传

## 第四阶段：补传与恢复能力

19. 按 `msgSvrId` 单条补传
20. 按 `createTime` 单条补传
21. 按时间段补传
22. 全量重扫
23. 断点续传
24. 主微信 / 分身微信双链路

---

# 23. 你真正需要交给 Codex 的“核心要求清单”

下面这段你可以几乎原样作为需求说明：

## 23.1 目标

在我的 Android App 中实现一套微信数据同步执行链，要求支持：

- 主微信和分身微信
- 微信 DB 备份与解密
- `message` 表增量读取
- 文本/图片/语音/视频/文件/VoIP 分类处理
- 媒体文件单独校验和上传
- 历史补传
- 本地游标和去重状态维护

## 23.2 关键约束

- DB 密钥通过 `IMEI + UIN` 的 MD5 前 7 位推导
- 必须能打开 SQLCipher 的 `EnMicroMsg.db`
- 必须将微信原始消息字段和本地待上传字段分开建模
- 文件上传必须先 check 再 upload
- 需要独立的媒体任务表
- 需要主/分身两套独立状态
- 需要保留 `msgSvrId` 修正规则
- 需要支持按 `msgSvrId` / `createTime` / 时间段补传

## 23.3 建议输出物

要求 Codex 产出：

- Kotlin 数据模型
- Room 表结构
- 微信 DB 读取层
- 消息分类器
- 媒体任务生成器
- 媒体上传器
- 同步状态管理器
- 前台 Service 版任务执行器
- 可直接运行的测试入口

---

# 24. 最后给你的判断

如果你的目标是“把他核心逻辑搬进自己的 App”，那这份源码里真正值得搬的核心，不是杂乱的类名和调用顺序，而是下面这套稳定骨架：

1. **任务驱动**：远程命令 / 本地命令统一进任务调度器  
2. **账号解析**：从 `systemInfo.cfg` 提取 `IMEI/UIN/wxId`，推导 DB key 和账号目录  
3. **SQLCipher 读库**：打开 `EnMicroMsg.db` / `SnsMicroMsg.db`  
4. **消息分流**：从 `message` 表读取完整字段，再裁剪上传字段  
5. **媒体任务化**：图片/语音/视频/文件/VoIP 不直接上传，先转媒体任务  
6. **先校验再上传**：MD5 + check 接口 + upload 接口 + 成功清理  
7. **增量与补传并存**：正常增量同步 + 单条/时间段/分类补传  
8. **主分身隔离**：主微信和分身微信两套状态完全分开  

你后面不需要再翻三份文档了，这一份就够作为总说明。

