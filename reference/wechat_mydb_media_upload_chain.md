# com.android.mydb 第三份附录：媒体文件真实上传 / 去重 / 校验 / 回调链路

> 文档目标：把 `com.android.mydb` 里与 **图片 / 语音 / 视频 / 文件 / VoIP / 朋友圈媒体** 相关的“真实上传链路”拆开说明。重点回答：
>
> 1. 文件是怎么被纳入上传队列的  
> 2. 先校验还是先上传  
> 3. 走 HTTP 直传还是走 OSS  
> 4. 如何避免重复上传  
> 5. 成功 / 失败后本地怎么清理  
> 6. 朋友圈文件为什么是单独链路  
>
> **说明**  
> - 这份文档基于反编译后的 `com.android.mydb` 源码整理。  
> - `UploadUtil.uploadFile(FileCheckModel, boolean)` 方法在反编译结果里未完全展开，因此该方法内部的“最终分流条件”不能 100% 逐行复原。  
> - 但从 `CheckFilesCallback / PostStringCallback / YunkeOkHttpUtil / URLConstant / MessageUtil / UploadUtil / DBUtil` 的调用关系，已经可以把主链路坐实到足够细。

---

## 1. 先给结论：媒体上传链路的总体结构

`com.android.mydb` 对“媒体文件”的处理不是简单发现文件就直接发给后端，而是分成下面几层：

1. **消息解析层**  
   从微信 `message` 表、`WxFileIndex3`、朋友圈对象等地方识别出要上传的媒体。

2. **本地索引层**  
   把待处理的媒体条目先落到本地表：
   - `WxFileIndex3_local`
   - `WxFileIndex3Slave_local`
   - `Emoji_local`
   - 或特殊情况下的 `message` 本地表（VoIP 相关）

3. **备份导出层**  
   通过 `WechatBackupManager.backupWxFileIndexImg(...)` / `backupWxFileIndexImgSlave(...)` 等厂商企业能力，把微信私有目录里的原始文件导到外部可访问目录。

4. **文件校验层**  
   计算 MD5，调用：
   - `wechat/checkWxFiles`
   - 朋友圈单独走 `wechatweb/wechatFriendster/check-files`

   目的：先问后台“这个文件是否已经有了”。

5. **真正上传层**  
   如果后台返回“需要上传”，才进入上传：
   - 普通微信媒体：`wechat/upfile` 或 OSS + `wechat/callback/upfile`
   - 语音：`wechat/uploadWechatVoiceFiles` 或 OSS 回调链
   - 朋友圈：`wechat/uploadFriendster` + 朋友圈文件接口
   - VoIP：`call/uploadWechatCall`

6. **本地去重层**  
   用 MD5 做本地“上传中 / 已成功”双重去重。

7. **回调清理层**  
   上传成功后：
   - 删除本地待上传记录
   - 标记 MD5 已上传
   - 删除或移动备份文件

   上传失败后：
   - 释放“上传中”标记
   - 某些场景删除本地队列表项
   - Wi-Fi 环境下允许后续重试，移动网络下有些场景直接放弃

---

## 2. 本地待上传索引：`WxFileIndex3` 是整个媒体链路的核心队列表

### 2.1 表结构

`DBUtil` 里能看到 `WxFileIndex3` 的建表语句：

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

这张表本质上是一个 **媒体导出 + 上传中间队列表**。

### 2.2 它的作用

它不保存消息正文，而是保存：

- 这个媒体属于哪条消息（`msgId / msgSvrId`）
- 属于什么消息类型（`msgType / msgSubType`）
- 微信私有目录里的源路径（`srcPath`）
- 备份导出后的路径（`outPath`）
- 某些“引用文件 / ref 文件”的路径（`srcRefPath / outRefPath`）
- 备份请求 ID（`requestId`）
- 导出后计算出的 MD5（`md5`）

也就是说，**消息解析层和真正网络上传层之间，就是靠 `WxFileIndex3` 接起来的。**

---

## 3. 媒体文件是怎么进入上传队列的

### 3.1 图片

图片链路主要由：

- `ImageMsgHandle`
- `ReceiverUtil`
- `UploadUtil.uploadImage(...)`

来完成。

从代码能确认：图片会按不同清晰度 / 子类型落入 `WxFileIndex3_local`：

- 小图：`msgSubType = 21`
- 高清小图：`msgSubType = 22`
- 高清图：`msgSubType = 26`

插入前会先查：

- `DBUtil.queryWxFileIndex3ItemExist(...)`

避免同一 `msgId + msgType + msgSubType + srcPath` 被重复入队。

### 3.2 视频

视频由 `MessageUtil.uploadVideo(...)` 生成上传任务。

它会拼接：

- `video/<imgPath>.mp4`

同时还会生成：

- `srcRefPath = 原路径 + "⌖"`
- `outRefPath = 导出路径 + "⌖"`
- 还尝试备份 `origin.mp4`

说明视频存在两层：

1. 实际视频文件  
2. 一个“ref 文件”或索引文件，用于找到真正媒体实体

### 3.3 普通文件 / 附件

普通文件由 `MessageUtil.uploadDocument(...)` 生成。

关键路径：

- 从 XML `content` 里取文件名 `title`
- 拼出子路径：`attachment/<title>`

如果文件名存在，还会同时生成：

- `srcRefPath = 原路径 + "⌖"`
- `outRefPath = 导出路径 + "⌖"`

并对 `.mp4` 文件走特殊 requestId。

### 3.4 普通语音

普通微信语音消息由 `MessageUtil.uploadVoice(...)` 处理。

虽然反编译片段被截断，但从：

- `MessageUtil.TYPE_VOICE = 34`
- `PostStringCallback` 对 `type == 4` 的专门分支
- `CheckFilesCallback` 对 `type == 4` 的专门删除逻辑

可以确认：

- **普通语音文件在文件上传层的 `FileCheckModel.type` = 4**

也就是说：

- 微信 `message.type = 34` 是消息类型
- 文件上传链路里的 `FileCheckModel.type = 4` 是“媒体分类编码”

这两个 `type` 不是同一个维度。

### 3.5 Emoji / 表情

表情消息走：

- `EmojiUploadManager`
- `EmojiUtil`

`CheckFilesCallback` 与 `UploadUtil.deleteUploadDbItem(...)` 都把：

- `FileCheckModel.type == 3`

作为 Emoji 单独处理。

说明 Emoji 不放在 `WxFileIndex3_local` 删除，而是走：

- `Emoji_local`
- `EmojiSlave_local`

### 3.6 VoIP 通话录音

VoIP 录音是单独链路。

在 `MessageUtil.voipVoiceCheckAndUpload(...)` 中：

1. 先从 `message.content` 里取 JSON `fileName`
2. 在两个目录里找实际文件：
   - `/storage/emulated/0/backup_wechat/wechatFile/`
   - `/storage/emulated/0/backup_voip/`
3. 找到后算 MD5
4. 构造 `FileCheckModel`
5. **这里的 `FileCheckModel.type = 5`**
6. 调 `UploadUtil.uploadFile(fileCheckModel, slave)`

注意：VoIP 这里给服务器的 `msgSvrId` 不是普通消息的 `msgSvrId`，而是：

- `messageModel.createTime`

也就是说 VoIP 录音在上传体系里更像“通话记录文件”，不严格依附原始微信消息主键。

### 3.7 朋友圈图片 / 视频

朋友圈完全单独，不走普通 `wechat/checkWxFiles` 这一套，而是：

- `friendsterProcessSnsList(...)`
- `friendsterCheckAndUploadBatch(...)`
- `friendsterCheckFiles(...)`
- `friendsterUploadFile(...)`
- `friendsterUploadCallback(...)`

并且朋友圈图片 / 视频会从 `SnsObjectModel` 中先组装成文件映射：

- 图片：`snst_<id>.jpg`
- 视频：`sight_<id>.mp4`

再走专门的朋友圈接口。

---

## 4. 真实文件导出：为什么有 `srcPath/outPath/srcRefPath/outRefPath`

### 4.1 `srcPath`

这是微信私有目录里的原始路径，例如：

- `.../MicroMsg/<accountDir>/image2/...`
- `.../MicroMsg/<accountDir>/video/...`
- `.../MicroMsg/<accountDir>/attachment/...`

### 4.2 `outPath`

这是通过企业备份能力导出的外部可访问路径。  
后续真正算 MD5、上传的文件通常发生在 `outPath` 这边。

### 4.3 `srcRefPath / outRefPath`

这两列是非常关键的细节。

代码里能看到：

- `detectdRefFile(...)`
- `detectdRefFileMp4(...)`

如果路径带 `⌖`，程序会把这个文件当成“引用文件 / 索引文件”去读。  
它会打开这个 ref 文件，把里面的内容再拼成真实路径：

- `image2/.ref/d/<token>.jpg`
- `video/.ref/d/<token>.mp4`
- `attachment/.ref/d/<token>`

这意味着：

**有些微信媒体不是直接按原始路径存放，而是先给一个 ref 文件，再通过 ref 文件内容定位真实实体。**

所以这套程序不是只会“复制固定文件”，而是已经适配了微信的引用式存储结构。

---

## 5. 文件上传前一定会先做“服务端校验”

### 5.1 普通微信媒体

普通媒体的校验接口是：

- `URLConstant.UPLOAD_checkWxFiles = .../wechat/checkWxFiles`

请求体本质上是一个 JSON 数组，每一项包含：

- `wechatId`
- `msgSvrId`
- `md5`
- `filename`
- `type`

这在：

- `UploadUtil.uploadImage(...)`
- `MessageUtil.voipVoiceCheckAndUpload(...)`
- `EmojiUploadManager`

里都能看到统一结构。

### 5.2 服务端怎么决定“是否需要上传”

`CheckFilesCallback` 是关键。

后端返回：

```json
{
  "code": 10000,
  "data": [...]
}
```

其中 `data` 里的条目表示：

- 这些 MD5 对应的文件，服务器判定 **需要上传**。

`CheckFilesCallback` 的逻辑是：

1. 把本地待校验 `List<FileCheckModel>` 建成 `md5 -> FileCheckModel` 映射
2. 遍历服务端返回的 `data`
3. 命中的 md5 才执行：
   - `UploadUtil.uploadFile(fileCheckModel, slave)`
4. 没被返回的本地文件视为“服务器不需要”，直接：
   - 删除本地待上传数据库项
   - 某些类型顺带删除备份文件

### 5.3 这一步的真实意义

这说明后端不是被动接收所有文件，而是先做存在性判断：

- 已有的文件不再上传
- 缺失的文件才真正发送

所以整体策略是：

**本地算 MD5 → 服务器判重 → 仅上传缺文件**

---

## 6. 本地去重：除了服务端判重，它还做了本地 MD5 去重

### 6.1 去重组件

类：

- `FileUploadDeduplicationManager`

它维护两层本地去重：

#### A. `uploadingList`

内存中的“上传中” MD5 列表。  
同一个 MD5 如果正在上传，会直接拦截。

#### B. `uploaded.txt`

路径：

- `<filesDir>/upload_cache/uploaded.txt`

成功上传过的 MD5 会被追加到这个文件。  
后续再遇到相同 MD5，会认为“已成功上传过”。

### 6.2 去重规则

`isFileAlreadyUploaded(FileCheckModel model)` 的逻辑是：

1. md5 为空则不处理
2. 若 `uploadingList` 已包含该 md5 → 判定重复（上传中）
3. 若 `uploaded.txt` 已包含该 md5 → 判定重复（历史成功）
4. 否则把 md5 加进 `uploadingList`

### 6.3 成功 / 失败后如何更新去重状态

#### 成功

- `markFileAsUploaded(model)`
  - 如果 `uploaded.txt` 不存在该 md5，就追加写入
  - 同时从 `uploadingList` 删除

#### 失败

- `markFileAsUploadFailed(md5)`
  - 只从 `uploadingList` 释放
  - 不写入 `uploaded.txt`

所以这套去重机制的本质是：

- **防止并发重复上传**
- **防止成功后再次上传**
- **失败后允许以后重试**

---

## 7. 真正上传到底走 HTTP 还是 OSS

### 7.1 可以确认：两种通道都存在

从 `YunkeOkHttpUtil` 可以确认，这个包至少支持两种上传方式：

#### A. HTTP 直传

- `uploadFileByOkhttpUtils(...)`
- `uploadFriendsterFile(...)`

对应接口：

- `wechat/upfile`
- `wechat/uploadWechatVoiceFiles`
- `wechat/uploadFriendster`
- 以及数据库类上传 `newWechat/uploadSqlLite`

#### B. OSS 上传

- `uploadFileByOSS(...)`
- `uploadFriendsterByOSS(...)`

上传成功后再回调服务端：

- 普通文件：`wechat/callback/upfile`
- 语音 OSS 回调：`newWechat/uploadWeChatVoiceOSSCallBack`
- 数据库 OSS 回调：`newWechat/uploadSqlLite/callBack`

### 7.2 普通文件 OSS 路径规则

普通文件 OSS object key 格式：

```text
{companyId}/weChatOssFile/{yyyy}/{MMdd}/{userId}/file/{wechatId}-2.9.3_MI-{md5}-{filename}
```

并且 bucket 会随文件类型切换：

- `mode.type == 0` → 图片 bucket
- 其他 → 文件 bucket

### 7.3 哪些类型更像走普通媒体上传

根据回调、日志和删除逻辑可以确认 `FileCheckModel.type` 至少有：

- `0`：图片
- `3`：Emoji / 表情
- `4`：普通语音
- `5`：VoIP 录音

而 `PostStringCallback` 里还有：

- `1`：视频
- `2`：其他文件

这说明 **媒体分类 type** 大致是：

| FileCheckModel.type | 语义 |
|---|---|
| 0 | 图片 |
| 1 | 视频 |
| 2 | 普通文件 / 附件 |
| 3 | Emoji / 表情 |
| 4 | 普通语音 |
| 5 | VoIP 录音 |

> 这个映射中，`0/3/4/5` 是代码里直接坐实的；`1/2` 来自 `PostStringCallback` 的成功/失败处理分支，也很可靠。

### 7.4 `UploadUtil.uploadFile(...)` 的分流：能确认到什么程度

虽然 `UploadUtil.uploadFile(...)` 主体反编译不完整，但从整体调用链能确定：

- 它接收一个已经通过本地解析、且通常经过服务端 check 的 `FileCheckModel`
- 内部会根据 `type`、网络环境、文件大小、配置项等做分流
- 最终不是走：
  - HTTP multipart 直传
  - 就是 OSS + callback

并且无论是哪条分支，成功 / 失败都由：

- `PostStringCallback`
- 或 `YunkeOkHttpUtil.OSSCallBack2Sever(...)`

完成最后的标记与清理。

因此即使 `uploadFile()` 本体没完全还原，主链路已经能被确认。

---

## 8. 成功后的处理：不是只有“上传成功”，还会做 3 类收尾动作

### 8.1 标记 MD5 已上传

不管 HTTP 还是 OSS，只要服务端业务成功：

- `FileUploadDeduplicationManager.markFileAsUploaded(model)`

这样后面同 MD5 会被本地直接拦截。

### 8.2 删除本地待上传记录

统一通过：

- `UploadUtil.deleteUploadDbItem(fileCheckModel, slave)`

逻辑是：

- `type == 5`（VoIP）→ 删除 `message` 本地表对应 `msgSvrId`
- `type == 3`（Emoji）→ 删除 `Emoji_local / EmojiSlave_local`
- 其他类型 → 删除 `WxFileIndex3_local / WxFileIndex3Slave_local` 中对应 md5

也就是说，**上传成功后会把“本地待处理索引”清掉，避免再次处理。**

### 8.3 删除或移动备份文件

`PostStringCallback` 对不同类型做了不同收尾：

#### 图片 `type == 0`

- 记录“图片上传完成”日志
- 备份文件通常直接删除

#### 普通语音 `type == 4`

- 记录“VOICE 上传完成”日志
- 直接删除本地备份语音文件

#### VoIP `type == 5`

- **不是直接删**
- 而是移动到：
  - `/storage/emulated/0/backup_voip/`

说明 VoIP 文件被当成一种长期留痕或单独归档资源。

#### 其他文件 / 视频

- 直接删除备份文件

---

## 9. 失败后的处理：既有“释放重试机会”，也有“直接放弃”

### 9.1 HTTP 失败

`PostStringCallback.doErrorThing(...)` 里：

1. 先调用：
   - `markFileAsUploadFailed(md5)`
2. 然后根据类型异步清理本地数据库项
3. 再根据类型处理本地文件：
   - 视频 / 文件 / 其他 → 删除文件
   - VoIP → 失败时不立即处理原文件

这说明 HTTP 失败时，很多场景会把“本地排队项”和“备份文件”都清掉，不一定保留到下次自动补传。

### 9.2 OSS 上传失败

`YunkeOkHttpUtil.uploadFileByOSS(...)` 的失败分支里：

- 先 `markFileAsUploadFailed(md5)`
- 然后分网络环境：

#### 非 Wi-Fi

- 认为是流量环境
- 直接按失败策略清理本地数据库项
- 删除对应备份文件
- 不重传

#### Wi-Fi

- 记录“稍后尝试重传”
- 不立刻把这条看成最终失败

这说明 OSS 分支对 Wi-Fi 更宽松，对移动网络更保守。

### 9.3 OSS 上传成功但 callback 失败

OSS 先上传到对象存储成功，还不算链路真正完成。  
之后还必须：

- `OSSCallBack2Sever(...)`
- 请求 `wechat/callback/upfile`

如果 callback：

- HTTP 失败
- 响应解析失败
- 或业务码不是 `10000`

都会执行：

- `markFileAsUploadFailed(md5)`

说明在系统眼里，**真正完成上传 = OSS 成功 + callback 成功**。

---

## 10. 朋友圈文件为什么单独处理

朋友圈媒体走的是完全独立的一套接口和 URL 规则。

### 10.1 单独接口

- `wechatweb/wechatFriendster/check-files`
- `wechat/uploadFiles`
- `wechat/callback/upfile`（朋友圈回调有自己包装）
- `wechat/uploadFriendster`

### 10.2 fileId / URL 规则

朋友圈文件不是简单按 `msgSvrId + md5` 定位，而是按：

- `fileId`
- `companyId`
- 扩展名（jpg / mp4）
- buildFriendsterUrl(...) 生成的 URL

所以朋友圈更像“业务对象文件上传”，不是普通聊天附件上传。

### 10.3 处理策略

`friendsterProcessSnsListInternal(...)` 里会把：

- 朋友圈图片
- 朋友圈视频

分别构建文件映射，然后批量：

1. 先校验是否缺失
2. 再上传缺失文件
3. 最后做 callback

因此，朋友圈媒体不能简单归到普通 `message` 附件链路里，它是独立产品线。

---

## 11. 语音 / VoIP / 视频 / 文件：收尾策略差异

这是源码里非常值得注意的一个点。

| 类型 | FileCheckModel.type | 成功后处理 | 失败后处理 |
|---|---:|---|---|
| 图片 | 0 | 标记成功、删本地索引、删备份文件 | 释放 md5、清理索引、常见情况下删文件 |
| 视频 | 1 | 标记成功、删索引、删文件 | 失败时删文件 |
| 普通文件 | 2 | 标记成功、删索引、删文件 | 失败时删文件 |
| Emoji | 3 | 删 Emoji 本地表项 | 失败时删 Emoji 本地表项 |
| 普通语音 | 4 | 删索引、删备份语音文件 | 失败释放 md5，常见情况下删文件 |
| VoIP 录音 | 5 | 删 message 本地项，文件移到 `backup_voip` | 失败时不一定马上删原文件 |

这意味着系统对 VoIP 的保留级别高于普通媒体。

---

## 12. 和消息主链的关系：什么时候真正触发媒体上传

这里有个容易忽略的点：

- 文字消息上传和媒体文件上传不是同一步

消息主链里：

1. `MessageUploadManager` / `NIMDBUtil` 先把消息、联系人、群等“结构化表”上传
2. `MessageUtil.generateMessage(...)` 再按消息类型生成媒体索引
3. `ReceiverUtil` / `WechatBackupManager` 收到备份完成后，把 `outPath`、`md5` 等补齐
4. 再走 `checkWxFiles -> uploadFile`

所以“媒体文件上传”本质上是：

**消息解析后的异步第二阶段**

而不是 `message` 表一上传就把全部媒体同步带走。

---

## 13. 这条链里最关键的几个判断条件

### 13.1 是否允许移动网络上传

`MessageUtil.uploadDocument(...)`、`uploadVideo(...)` 里都判断：

- `PeriodicWorkUtil.useMobileNet`
- 当前网络是否 `WIFI`

如果配置不允许移动网络，且当前不是 Wi-Fi，则直接：

- 不上传文件

### 13.2 文件大小限制

`PeriodicWorkUtil` 和 `MessageUtil.isVideoBig(...)` 会判断：

- `Constants.FILE_UPLOAD_LIMIT_MAX`

超过限制的媒体可能：

- 不进入上传
- 或被标记 `bigFileFlag`
- 是否上传再由 `GZTHelper.isWechatFileUpload50m(...)` 等配置决定

### 13.3 是否已经存在本地队列项

- `DBUtil.queryWxFileIndex3ItemExist(...)`

避免重复插入索引。

### 13.4 是否已经上传过

- `FileUploadDeduplicationManager.isFileAlreadyUploaded(...)`

避免重复发网请求。

### 13.5 服务器是否需要这个文件

- `wechat/checkWxFiles`
- `friendster/check-files`

后台说“不需要”，本地直接删待上传项。

---

## 14. 这份源码能坐实的“上传接口清单”

### 14.1 普通微信消息附件 / 媒体

- `newWechat/uploadSqlLite`  —— 表/SQLite 数据上传
- `wechat/checkWxFiles` —— 文件存在性校验
- `wechat/upfile` —— 文件上传
- `wechat/uploadFiles` —— 朋友圈文件上传（更偏文件层）
- `wechat/callback/upfile` —— OSS 上传成功后的服务端确认回调

### 14.2 登录 / 状态类

- `wechat/saveWeChatLoginLog`

### 14.3 语音 / VoIP

- `newWechat/uploadWeChatVoice`
- `wechatweb/call/uploadWeChatVoice`
- `wechat/uploadWechatVoiceFiles`
- `call/uploadWechatCall`
- `newWechat/uploadWeChatVoiceOSSCallBack`

### 14.4 朋友圈

- `wechat/uploadFriendster`
- `wechatweb/wechatFriendster/check-files`
- `wechat/uploadFiles`
- 朋友圈 callback 接口

---

## 15. 最终还原：真实媒体上传流程图（文字版）

### 15.1 普通聊天图片 / 视频 / 附件 / 语音

1. 从 `message` 表识别媒体消息  
2. 生成 `WxFileIndex3_local` 队列表项  
3. 调厂商备份能力，把微信私有文件导到外部目录  
4. 备份完成后补全 `outPath / md5`  
5. 构造 `FileCheckModel`  
6. 本地去重检查（上传中 / 已成功）  
7. 请求 `wechat/checkWxFiles`  
8. 服务端返回需要上传的 md5  
9. 才进入 `uploadFile(...)`  
10. `uploadFile(...)` 再分流为 HTTP 或 OSS  
11. 成功后：
    - 标记 md5 已上传
    - 删除本地待上传索引
    - 删除备份文件
12. 失败后：
    - 释放 md5 上传中标记
    - 按网络与类型决定是否删除文件 / 是否保留重试机会

### 15.2 VoIP 录音

1. 从消息内容 JSON 取 `fileName`  
2. 在 `backup_wechat/wechatFile` 或 `backup_voip` 找文件  
3. 计算 md5  
4. 走普通文件上传链，但 `FileCheckModel.type = 5`  
5. 上传成功后移动到 `/storage/emulated/0/backup_voip/`

### 15.3 朋友圈图片 / 视频

1. 从 `SnsObjectModel` 解析媒体  
2. 构造 `fileId -> file` 映射  
3. 请求 `wechatweb/wechatFriendster/check-files`  
4. 缺失的才上传  
5. 上传后做 callback 通知后端

---

## 16. 最终结论

`com.android.mydb` 的媒体上传设计不是“发现文件就直接发”，而是一套比较完整的四段式机制：

- **本地消息解析与索引入队**
- **企业备份能力导出真实文件**
- **服务端 MD5 判重后再上传**
- **本地 MD5 持久化去重与成功/失败清理**

其中最关键的技术点有四个：

1. 用 `WxFileIndex3` 作为媒体异步队列  
2. 用 `checkWxFiles` 先问服务器“缺不缺”  
3. 用 `FileUploadDeduplicationManager` 做本地 MD5 双重去重  
4. 对 VoIP / 朋友圈单独建链，不和普通聊天附件混用

如果只看“图片/文件/语音真实上传链路”，这份包已经足够证明：

- 它不只是上传聊天文本  
- 它会真实导出微信私有媒体  
- 会做本地 MD5、服务端判重、对象存储/HTTP 上传、回调确认、删除本地痕迹  
- 并且对不同媒体类型采用了不同清理和保留策略

---

## 17. 建议的第四份文档方向

如果继续往下深挖，下一份最值得补的是：

### 《DBUtil / NIMDBUtil 的 SQL 查询与增量同步机制》

重点可以写：

- `message` / `rcontact` / `chatroom` / `SnsInfo` 的实际 SQL
- 增量同步位置怎么记（SharedPreferences / position）
- `msgId / createTime / snsId / luckyMoney` 各自怎么做断点续传
- 历史补传和日常增量同步怎么共用底层逻辑

