# Android 测试、安全、性能与交付

## 1. 测试分层

| 层 | 工具 | 必测内容 |
| --- | --- | --- |
| Domain/data unit | JUnit + coroutines-test | mapper、error、idempotency、session、repository、use case。 |
| Flow/ViewModel | Turbine 或等价 Flow 测试 | action→UiState、并发刷新、恢复、消息确认。 |
| HTTP contract | MockWebServer + shared fixture | headers、JSON/nullability、错误 envelope、timeout、401 refresh。 |
| Compose component | Compose UI test | semantics、输入、loading/error、card selection、timeline。 |
| Navigation/integration | Hilt test + fake repository | auth graph、5 back stacks、deep link、process recreation。 |
| Device UAT | emulator/physical device | API 24 与 target API、Photo Picker、网络切换、TalkBack。 |
| Performance | Macrobenchmark/Baseline Profile（垂直切片稳定后） | startup、scroll、关键 Compose route。 |

unit 测试直接构造 ViewModel/repository 并传 fake；不需要为了 Hilt 而启动 Android runtime。

当前包含 Compose instrumentation smoke tests，以及覆盖 bearer、401 refresh、correlation/idempotency header、记录 CRUD `If-Match`、公开 endpoint path 和本地食材缩放的 JVM 测试。当前工作站没有可用 Android SDK/emulator，因此本次交付不声称重新运行了 device tests。

## 2. 必测场景

### Session/network

- refresh token 加密 round-trip、key 不可导出、备份排除；
- 多并发 401 单飞 refresh；Authenticator 不递归；
- 403 不 refresh；logout-all 清 back stack/cache；
- release logging 无 header/body；local cleartext 只允许明确 host；
- timeout/offline/429/5xx 映射准确。

### State/lifecycle

- rotate/process recreation 后 ID、filter、selected recipes 恢复；
- 离屏停止不必要 collection；回前台刷新不清空旧数据；
- snackbar/message state 不丢失、不重复；
- pending command 不因重组重复提交。

### Recommendation/Cooking

- “换一个”不产生网络请求；
- generate idempotency key 生命周期；
- Cooking 四状态和 timeline semantics；
- 添加菜谱入口只存在 Saved/Recipes；
- shared fixture 与 Web 显示同一业务值。

## 3. 安全基线

### Manifest 与存储

- 只新增 `INTERNET` 等必要权限；不请求广泛存储/位置/联系人权限。
- release `android:debuggable=false`、组件 `exported` 最小化。
- Manifest 仅声明 `INTERNET`；debug cleartext 只允许 emulator host `10.0.2.2`，release 不启用 cleartext。
- session 密文、用户 cache 和临时上传信息从 backup/data extraction 排除。
- 使用 Android Keystore + AES/GCM；不自定义算法、不用已弃用 `security-crypto` convenience API。

### 网络

- staging/production 只允许 HTTPS；`network_security_config` base cleartext false。
- localDebug 仅对明确 emulator host/domain 开 cleartext，不能全局开启。
- 默认使用平台 CA；证书 pinning 只有具备轮换、backup pin 和运维能力时才采用。
- OkHttp 保持安全更新；dependency scan 处置 TLS/serialization 公告。

参考：[Android Keystore](https://developer.android.com/privacy-and-security/keystore)、[Network Security Configuration](https://developer.android.com/privacy-and-security/security-config)、[备份安全建议](https://developer.android.com/privacy-and-security/risks/backup-best-practices)。

### 输入与 Web/LLM 内容

- recipe/chat/group text 只作文本显示，不解析成 HTML/Intent/命令。
- deep link、返回 URL、content URI 做 allow-list 和 ownership 检查。
- 不允许服务端文本指定任意 URL scheme；外链经确认页面/允许域策略。
- 客户端验证不是授权/业务安全边界。

## 4. 性能与稳定性预算

首个真实垂直切片后记录基线，目标：

| 指标 | 目标 |
| --- | --- |
| cold start（中端设备 release，P50） | ≤ 1.5 s 到可交互 shell |
| janky frames（核心列表） | < 5% |
| ANR/crash（演示/UAT） | 0 已知阻塞问题 |
| APK 体积 | 每次依赖变更记录差异；未评审不增加大型重复库 |

措施：

- Lazy list 使用稳定 key/contentType，避免在 item 中创建昂贵对象；
- immutable UI models、窄 state selection，避免整屏无谓重组；
- Coil 请求按显示尺寸解码，列表缩略图不加载原图；
- 图表/大详情延迟创建，不阻塞首页；
- release 开 R8/resource shrink 并验证序列化/Retrofit keep rules；
- 垂直切片稳定后生成 Baseline Profile，不在架构仍频繁变化时提前维护。

## 5. CI 门槛

目标 GitHub Actions：

1. checkout、JDK 17、Gradle cache；
2. OpenAPI lint + Kotlin clean generation/compile diff；
3. `./gradlew lintDebug testDebugUnitTest`；
4. `./gradlew assembleMockDebug assembleStagingDebug`；
5. Compose/navigation instrumentation smoke（managed device/emulator）；
6. dependency/secret scan；
7. 上传 lint、test、coverage、screenshots 和 debug APK；
8. tag/demo release 才运行 release assemble、R8 smoke、签名后的受控分发流程。

签名材料只在受保护 CI secret/keystore 中；debug keystore 不作为 release 签名。

## 6. Definition of Done

一个 Android feature 只有在以下全部满足时才完成：

- 原生 Compose screen、Navigation 3、ViewModel/StateFlow 和 repository 边界完整；
- 使用已记录 OpenAPI 版本和 mapper；没有 UI→Retrofit 直连；
- loading/empty/error/fallback/offline/process recreation 均实现；
- unit/Flow/MockWebServer/Compose 测试通过，共享 UAT 有证据；
- 48dp、fontScale 2.0、TalkBack、insets、compact/expanded 检查通过；
- lint/test/assemble/CI 通过，无未处置 Critical/High 依赖风险；
- release 无 mock、cleartext、body log、secret、未授权资产；
- Web 端的权限、错误、指标和 AI 状态语义一致；
- 文档、截图和 OpenAPI hash 已更新。
