# com.android.mydb 微信消息附录分析

> 说明：本附录基于你提供的 `mydb.zip` 反编译源码整理，重点聚焦：
>
> 1. 微信 `message` 表读取字段
> 2. 本地上传表最终保存字段
> 3. `message.type` 的关键类型值与分流逻辑
> 4. 文本 / 图片 / 语音 / 视频 / 文件 / VoIP 的识别依据
> 5. 媒体文件路径推导与上传动作
>
> 这份文档只写目前在源码里能直接坐实的内容；对微信内部协议细节、未完整展开的方法和未给出实现的上传接口，不做过度推断。

---

## 1. 先说结论

`com.android.mydb` 的消息处理不是“把整个微信数据库直接上传”，而是：

1. 从微信 `EnMicroMsg.db` 的 `message` 表按批读取消息行
2. 读取较完整的字段集（不仅有 `content`，还有 `reserved`、`lvbuffer`、`imgPath`、`biz*` 等）
3. 先在本地做一次裁剪与规整
4. 把“结构化消息元数据”落到自己本地的 `message` 表
5. 再根据 `type` 和若干辅助字段触发媒体文件备份与上传

所以它的设计是：

- **文本类元数据**：结构化上传
- **媒体类文件**：单独索引、单独备份、单独上传
- **历史补传**：按 `msgSvrId` / `createTime` / 时间段 / 特定类别重跑

---

## 2. 微信原始 `message` 表里实际读取了哪些字段

源码位置主要在：

- `com.android.mydb.database.DBUtil`
- `com.android.mydb.model.MessageModel`

### 2.1 批量增量读取 SQL

`DBUtil` 对微信原始 `message` 表的核心查询，字段列表是：

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

常见查询方式包括：

- 按 `msgId > oldMsgId` 增量取
- 按 `createTime > ?` 增量取
- 按 `createTime between ? and ?` 做时间段追溯
- 按 `msgSvrId = ?` 精确追溯
- `ORDER BY msgId DESC limit 1` / `ORDER BY createTime DESC LIMIT 1` 获取游标位置

### 2.2 `MessageModel` 里明确持有的字段

`MessageModel` 中可见字段如下：

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

### 2.3 `readFromCursor()` 实际赋值的字段

`MessageModel.readFromCursor()` 里直接从游标赋值的字段有：

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

说明：

- 查询 SQL 是“更全量”的
- 但不同路径里并不是每次都会把所有字段都持久化
- `reserved`、`lvbuffer` 等字段主要用于解析特定类型消息，不一定都会进入最终上传表

---

## 3. 本地“待上传 message 表”最终保存哪些字段

源码位置：

- `DBUtil.initTableNoneTransaction(...)`
- `DBUtil.convert2Value(MessageModel)`
- `DBUtil.convert2ValueFromLocal(MessageModel)`

### 3.1 本地 message 表结构

`initTableNoneTransaction()` 创建的本地 `message` 表字段是：

```sql
CREATE TABLE IF NOT EXISTS message (
  msgId LONG PRIMARY KEY,
  msgSvrId LONG,
  type INTEGER,
  status INTEGER,
  isSend INTEGER,
  createTime INTEGER,
  talker TEXT,
  content TEXT,
  transContent TEXT,
  talkerId INTEGER,
  flag INTEGER,
  imgPath TEXT,
  bigFileFlag INTEGER
)
```

### 3.2 `convert2Value(MessageModel)` 最终写入字段

最终落到本地待上传表的字段是：

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

### 3.3 没进入最终上传表、但会参与解析的字段

以下字段在读取时会拿到，但**默认不会直接进入本地上传表**：

- `reserved`
- `lvbuffer`
- `transBrandWording`
- `bizClientMsgId`
- `bizChatId`
- `bizChatUserId`
- `msgSeq`
- `solitaireFoldInfo`
- `isShowTimer`

它们的主要作用更像：

- 解析特殊消息内容
- 推断媒体文件信息
- 处理企业/业务会话字段
- 处理 VoIP 提示文案和录音线索

也就是说：

**这个程序对 message 表做的是“先读全，再裁剪上传”。**

---

## 4. `msgSvrId` 的替换规则

源码位置：

- `MessageModel.reSetAndGetMsgSvrId()`
- `MessageModel.replaceMsgSvrId(...)`

### 4.1 发送方消息的特殊处理

当满足以下条件时，会把 `msgSvrId` 重置为 `createTime`：

- `type == 50`（VoIP 类）
- `type == 64`
- 或者 `isSend == 1`

### 4.2 空 `msgSvrId` 的兜底

如果原 `msgSvrId` 为空或为 `0`，它会兜底为：

- `P-<createTime>`

说明：

这个系统非常依赖 `msgSvrId` 作为消息去重 / 文件回溯 / 上传删除的关键键值，所以会主动修正它。

---

## 5. `message.type` 的关键类型值

源码里能直接坐实并被分流处理的关键类型如下。

### 5.1 文本

- `type = 1`

这类消息走普通结构化消息上传，不会额外触发媒体文件备份。

### 5.2 图片

- `type = 3`

命中后会调用：

- `ImageMsgHandle.uploadImg(...)`

说明图片消息会单独做图片文件处理，而不是只传文本元数据。

### 5.3 语音

- `type = 34`

命中后会调用：

- `uploadVoice(...)`

并基于 `imgPath` 推导 AMR 文件路径。

### 5.4 视频

- `type = 43`

命中后会调用：

- `uploadVideo(...)`

并且还会根据 `reserved` 判断是不是“大视频文件”。

### 5.5 表情

- `type = 47`

命中后会调用：

- `EmojiUtil.backupEmojiMsg(...)`

### 5.6 VoIP / 通话消息

- `type = 50`

命中后会调用：

- `parseVoipContent(...)`
- `voipVoiceCheckAndUpload(...)`

这类消息会尝试从：

- `content`
- `lvbuffer`
- 备份录音目录

联合解析出通话类型、状态、时长、录音文件名。

### 5.7 文件消息

- `type = 1090519089`

命中后会调用：

- `uploadDocument(...)`

它会从 XML `content` 里取附件标题/文件名，并生成 `attachment/<文件名>` 路径。

### 5.8 代码里还出现的其它值

源码里还能看到与 `msgSvrId` 特殊处理相关的：

- `type = 64`

但当前给出的核心媒体分流逻辑里，没有看到它对应独立的上传分支。

---

## 6. 文本 / 图片 / 语音 / 视频 / 文件 / VoIP 是怎么区分的

这套逻辑不是靠单一字段，而是：

- `type` 做一级分类
- `content` / XML 做二级解析
- `imgPath` 做媒体路径定位
- `reserved` 做文件大小 / 视频扩展信息判断
- `lvbuffer` 做 VoIP / 引用消息等特殊字段补充

### 6.1 文本消息

判断依据：

- `type = 1`

保留与上传的核心字段：

- `msgId`
- `msgSvrId`
- `type`
- `status`
- `isSend`
- `createTime`
- `talker`
- `content`
- `talkerId`
- `flag`
- `transContent`（如有）

### 6.2 图片消息

判断依据：

- `type = 3`

处理动作：

- 进入 `ImageMsgHandle.uploadImg(...)`
- 由图片相关工具类和 `WxFileIndex3` 逻辑进一步处理原图/缩略图/引用路径

说明：

你当前这份源码里，图片细分路径逻辑更多分散在 `ImageMsgHandle`、`ImageUtil`、`WxFileIndexUtil`、`ImgInfo2Model` 这类类中；当前附录只把主线整理出来。

### 6.3 语音消息

判断依据：

- `type = 34`

路径推导规则：

1. 对 `imgPath` 做 `MD5`
2. 取 MD5 前 2 位、再取第 3-4 位
3. 组成：

```text
voice2/<md5[0:2]>/<md5[2:4]>/msg_<imgPath>.amr
```

然后拼到微信账号目录下。

主微信典型路径：

```text
/data/user/0/com.tencent.mm/MicroMsg/<accountDir>/voice2/.../msg_<imgPath>.amr
```

同时还会构建导出目录路径，并写入 `WxFileIndex3`，再调用：

- `WechatBackupManager.backupWxFileIndexImg(...)`

说明语音不是直接从 DB 字段上传，而是：

- DB 决定“这是一条语音消息”
- `imgPath` 决定具体 AMR 文件位置
- 再走备份/文件上传链路

### 6.4 视频消息

判断依据：

- `type = 43`

路径推导规则：

```text
video/<imgPath>.mp4
```

除了主 MP4 外，它还会处理：

- `...mp4⌖`
- `origin.mp4`
- 参考路径 `srcRefPath` / `outRefPath`

说明这个符号路径和 origin 文件，明显是为了兼容：

- 参考文件
- 原始视频
- 可能的转码/索引关系

#### 大视频判断

调用：

- `isVideoBig(messageModel)`

依据：

- 从 `reserved` 里解析 XML
- 取 `length`
- 与 `Constants.FILE_UPLOAD_LIMIT_MAX` 比较

如果超限，会把：

- `bigFileFlag = 1`

### 6.5 文件消息

判断依据：

- `type = 1090519089`

路径推导规则：

先从 XML `content` 里取：

- `title`

然后拼：

```text
attachment/<title>
```

如果标题是 `.mp4` 结尾，说明它把某些 mp4 也归到附件消息链路处理，并使用另一组 requestId。

#### 大文件判断

调用：

- `isFileBig(messageModel)`

虽然你这份反编译代码里这段展示不完整，但能看出它也是从 `content` 或相关 XML 中取文件大小并和上传阈值比较，然后设置：

- `bigFileFlag = 1`

### 6.6 表情消息

判断依据：

- `type = 47`

处理动作：

- `EmojiUtil.backupEmojiMsg(...)`

说明表情会走独立的 Emoji 文件链路，而不是被当作普通文本。

### 6.7 VoIP / 通话消息

判断依据：

- `type = 50`

它的识别和解析比其它类型复杂得多。

#### 一级判断：`content`

调用：

- `parseVoipTypeFromXml(content)`

如果 `content` 是 XML，会尝试读取：

- `room_type`

判断是：

- 视频通话
- 语音通话

#### 二级判断：`lvbuffer`

它会把 `lvbuffer` 解成 `MessageLvbuff`，重点取：

- `msgSource`

然后根据 `msgSource` 里的中文提示词解析状态与时长，例如：

- `通话时长`
- `聊天时长`
- `通话中断`
- `聊天中断`

还会用正则匹配：

- `HH:MM`
- `HH:MM:SS`

来算时长。

#### 录音文件定位

最后它会去这些目录里找录音文件：

- `/storage/emulated/0/backup_wechat/wechatFile/`
- `/storage/emulated/0/backup_voip/`

并根据时间窗口 + 时长匹配 mp3 文件。

#### 最终生成的 VoIP 内容 JSON 线索

它会构造一个 JSON，大致包含：

- `type`
- `status`
- `startTime`
- `endTime`
- `duration`
- `fileName`

说明：

VoIP 消息上传时，`content` 很可能已经不是微信原始 XML，而是被替换成“解析后的通话摘要 JSON”。

---

## 7. `content` / `reserved` / `lvbuffer` 各自扮演什么角色

### 7.1 `content`

它是最基础的消息正文来源，但在不同类型中作用不同：

- 文本：直接就是正文
- 文件：通常是 XML，里面含文件标题/附件信息
- VoIP：通常是 XML，里面含房间类型等基础信息
- 某些引用/业务消息：也会嵌套 XML 结构

### 7.2 `reserved`

它在视频 / 大文件判断里非常关键。

源码里能看到：

- 如果 `reserved` 以 `<?xml` 开头，直接按 XML 解析
- 否则尝试按 `:` 分隔取后半段再解析 XML
- 然后读取 `length`

这说明 `reserved` 常被用来承载：

- 文件大小
- 视频元信息
- 某些扩展字段

### 7.3 `lvbuffer`

它主要用于微信私有二进制扩展字段解析，当前源码里最典型的用途是：

- VoIP 提示文案/状态解析
- 引用消息补充信息解析

也就是说：

**`lvbuffer` 是 message 行里最像“扩展协议区”的字段。**

---

## 8. 历史补传怎么和 message 表联动

微信补传入口在：

- `BootReceiver`
- `NIMDBUtil`

可以按以下维度回查消息：

### 8.1 按 `msgSvrId`

用于精确补某条消息。

### 8.2 按 `createTime`

按时间点或时间段扫描消息。

### 8.3 按类别重跑

源码里明确能看到：

- 朋友圈补传
- 红包补传
- 主微信/分身微信全量备份

历史消息在进入“历史上传本地库”前，还会补做这些事：

- `type == 50`：把 `content` 替换成 `parseVoipContentByBackUp()` 结果
- `type == 43`：判断大视频并设置 `bigFileFlag`
- `type == 1090519089`：判断大文件并设置 `bigFileFlag`

说明历史补传不是简单重发，而是会重新做类型解析与文件大小判断。

---

## 9. 消息表数据与文件上传是怎么串起来的

这条链路很重要，可以总结成：

### 9.1 DB 行先落本地 message 表

消息行被转换成结构化元数据，进入本地待上传表。

### 9.2 媒体类再落 `WxFileIndex3`

对图片 / 语音 / 视频 / 文件，会再生成：

- `WxFileIndex3`

字段包括：

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

### 9.3 然后再触发备份/上传

触发入口典型包括：

- `WechatBackupManager.backupWxFileIndexImg(...)`
- `UploadUtil.uploadFile(...)`

说明真正的媒体文件上传，依赖的是：

- 消息表 + 索引表 + 备份任务

不是单独扫目录。

---

## 10. 这份源码里能坐实的“上传到后端”的消息级关键信息

从当前源码能确定，消息级结构化上传至少包含：

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

其中：

- **文本类**：主要靠这些字段本身完成上传
- **媒体类**：这些字段只是“索引和元数据”，真正文件本体走文件上传接口
- **VoIP**：`content` 可能先被改写成摘要 JSON 再上传

---

## 11. 我对这套消息设计的判断

这套实现不是“尽可能原样复制微信消息表”，而是一个典型的“采集引擎中间层”设计：

### 保留的部分

保留了最关键的上传和关联键：

- 消息身份：`msgId` / `msgSvrId`
- 时间：`createTime`
- 会话对象：`talker`
- 内容：`content`
- 类型：`type`
- 文件定位线索：`imgPath`

### 丢弃或不直接持久化的部分

很多微信内部扩展字段没有直接落入最终上传表，例如：

- `reserved`
- `lvbuffer`
- `bizChat*`
- `msgSeq`
- `transBrandWording`

说明他们更看重：

- “能上传、能追溯、能找到文件、能做展示/检索”

而不是完整镜像微信内部 message 表结构。

---

## 12. 目前仍然值得继续深挖的点

如果你下一步还要继续往下钻，最值得看的 4 个点是：

### 12.1 `ImageMsgHandle`

这里大概率决定：

- 图片原图 / 缩略图 / 引用图
- 图片文件名 / 路径推导
- 图片上传与去重方式

### 12.2 `WxFileIndexUtil`

这里决定：

- 媒体索引如何落表
- `requestId` 如何编码不同文件类型
- `srcPath/outPath/srcRefPath/outRefPath` 的规范

### 12.3 `UploadUtil`

这里决定：

- 文件校验接口怎么调用
- 文件是否先 `checkWxFiles` 再真正上传
- 成功后如何删除 message / index 记录

### 12.4 `NIMDBUtil`

这里决定：

- `msgSvrId` / `createTime` 精准补传
- 历史窗口追溯
- 增量游标维护

---

## 13. 最终一句话总结

如果只聚焦微信 `message` 表，这个包的核心方法可以概括成：

**先从微信原始 `message` 表读取完整消息字段，再根据 `type + content + imgPath + reserved + lvbuffer` 做消息分类，最后把结构化元数据和媒体文件索引分开上传。**

其中消息类型里最关键的几类是：

- `1`：文本
- `3`：图片
- `34`：语音
- `43`：视频
- `47`：表情
- `50`：VoIP/通话
- `1090519089`：文件

