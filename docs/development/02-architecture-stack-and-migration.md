# Android 架构、技术栈与迁移

## 1. 目标技术栈

当前仓库保留 AGP 9.2.1、targetSdk 36、minSdk 24；compileSdk 从 36.1 升至 37，以满足现有 AndroidX Core 1.19 的 AAR metadata 要求。compileSdk 升级不等于立即选择 target API 37 的运行时行为，targetSdk 升级应作为独立 UAT 任务。以下为 2026-08-01 的 spike 候选，合并前需在 version catalog 固定并验证兼容：

| 关注点 | 选择/候选基线 | 说明 |
| --- | --- | --- |
| SDK/JVM/Kotlin | compileSdk 37、targetSdk 36、JDK 17、Kotlin Android 2.3.21、Compose compiler plugin 同 Kotlin | AGP 9.2 支持 API 37并要求 JDK 17。 |
| UI | Compose stable BOM `2026.06.00`、Material 3 | 只用 stable BOM，不逐个漂移 Compose 版本。 |
| 导航 | Navigation 3 stable `1.0.1` | 类型化 key、多根 back stack、自适应。 |
| 状态 | ViewModel、StateFlow、Coroutines、Lifecycle Compose | UDF 与 lifecycle-aware collection。 |
| DI | Hilt `2.57.1` + KSP | constructor injection；测试直接传 fake。 |
| 网络 | Retrofit 3.x + OkHttp 5.x + kotlinx.serialization | 版本组合由 spike 和 BOM/catalog 固定。 |
| API 生成 | OpenAPI Generator Kotlin `jvm-retrofit2` spike | C-01 后生成；不让 generated DTO 进入 UI。 |
| 设置/会话密文 | DataStore + Android Keystore | 不用已弃用 EncryptedSharedPreferences。 |
| 图片 | Coil 3.4.x | Compose 图片、缓存、取消。 |
| 图表 | Vico 3.2.x | 服务端指标渲染。 |
| 测试 | JUnit、kotlinx-coroutines-test、Turbine、MockWebServer、Compose UI test | unit 优先 fake，不强依赖 mocking framework。 |

具体补丁版本在实现当天通过官方 stable channel 和依赖扫描确认；禁止 `+`、snapshot、alpha。

## 2. 目标 package 结构

四周 MVP 先保持单 `app` Gradle module，使用严格 package 边界：

```text
com.foodmind.foodmind_android/
├─ app/
│  ├─ FoodMindApplication.kt
│  ├─ MainActivity.kt
│  ├─ FoodMindApp.kt
│  └─ navigation/
├─ core/
│  ├─ designsystem/
│  ├─ model/
│  ├─ network/
│  ├─ session/
│  ├─ common/
│  └─ testing/
├─ data/
│  ├─ remote/generated/       # never hand-edit
│  ├─ remote/mapper/
│  ├─ local/
│  └─ repository/
├─ domain/
│  ├─ model/
│  ├─ repository/
│  └─ usecase/
└─ feature/
   ├─ auth/
   ├─ home/
   ├─ recommendation/
   ├─ cooking/
   ├─ recipes/
   ├─ records/
   ├─ groups/
   ├─ explore/
   ├─ saved/
   ├─ chat/
   └─ analytics/
```

只有在并行团队冲突、构建时间或访问边界有实际证据时再拆 Gradle modules；不照搬 Now in Android 的完整模块数量。

## 3. 依赖方向

```text
feature UI → ViewModel → use case (需要时) → repository interface
                                               ↑
                             repository implementation → remote/local source
```

- Composable 不直接调用 Retrofit、DataStore 或 repository。
- ViewModel 不持有 `Activity`、`Context`、`Resources` 或 `NavController`。
- repository 是公开数据操作入口；remote DTO 留在 data 层。
- domain 不 import Android framework、Retrofit、OkHttp、Compose 或 generated package。
- 跨 feature 只共享 core/domain contract，不 import 对方内部 screen/ViewModel。

## 4. UDF 与 UI state

```kotlin
data class CookingSelectUiState(
    val recipes: AsyncValue<List<RecipeCardModel>> = AsyncValue.Loading,
    val selectedIds: Set<RecipeId> = emptySet(),
    val query: String = "",
    val submission: AsyncValue<PlanId>? = null,
    val userMessage: UserMessage? = null,
)

sealed interface CookingSelectAction {
    data class SearchChanged(val value: String) : CookingSelectAction
    data class RecipeToggled(val id: RecipeId) : CookingSelectAction
    data object GenerateClicked : CookingSelectAction
    data class MessageDismissed(val id: Long) : CookingSelectAction
}
```

规则：

- screen-level ViewModel 暴露不可变 `StateFlow<UiState>`，UI 用 `collectAsStateWithLifecycle()`。
- state 向下、action 向上；Composable 不在 render 阶段启动请求。
- 导航点击本身由 UI 发给 app navigation state；依赖保存结果的导航以可恢复 state 表达，并在消费后显式确认。
- 不用丢失型 `Channel`/`SharedFlow` 发送 snackbar/navigation “一次性事件”；消息进入 state，带 ID 并可确认消费。
- 短 UI 状态尽量 `rememberSaveable`；业务/远程状态放 ViewModel/repository。

## 5. Repository 与 use case

- 一个 repository 对应一种业务数据集合，而不是一个 endpoint。
- 简单透传不强制创建 use case；只有跨 repository 协调、幂等 key 生命周期或可复用客户端行为才使用 use case。
- 客户端 use case 不复制权限、hard filter、推荐算法、指标或 Agent 验证。
- repository 将 transport/backend failure 映射为 sealed domain failure。

## 6. OpenAPI 生成边界

目标生成参数需先通过 spike：

```text
generator: kotlin
library: jvm-retrofit2
serializationLibrary: kotlinx_serialization
useCoroutines: true
useResponseAsReturnType: true
packageName: com.foodmind.foodmind_android.data.remote.generated
```

必须验证：

- OAS 3.1 nullability、`allOf`、UUID/date-time、multipart/presigned media；
- enum unknown value 策略；
- 401/error body 仍可解析；
- `Idempotency-Key`、`X-Correlation-ID` header；
- API 24–36 的 runtime 兼容。

禁止开启 generator 的 `supportAndroidApiLevel25AndBelow` 不安全兼容开关；本项目 minSdk 24 时若生成代码需要临时文件，必须改模板/手写窄 adapter 或调整策略，不能接受已知漏洞。

## 7. Build variants

```text
flavors: mock, local, staging, productionDemo
buildTypes: debug, release
```

- `mockDebug`：fake repository/fixture，可离线演示 UI，显式水印或 debug 标识。
- `localDebug`：`10.0.2.2` 的本地 Spring Boot，仅此变体允许特定 cleartext host。
- `stagingDebug/release`：HTTPS staging，真实集成与 UAT。
- `productionDemoRelease`：HTTPS production-demo，R8、无 body logging、无 mock。

base URL 通过 BuildConfig/DI qualifier 注入，不写进 screen；任何服务 token/LLM key 都不能进入 APK。

## 8. XML → Compose 迁移

采用可回退的小步迁移：

1. compileSdk 37/JDK 17 基线已修复；Cooking 时间线先以 `CookingPlanViewModel` + `StateFlow` 建立 UDF 边界，再升级 Kotlin/Compose/Hilt，保留现有 XML 可运行。
2. 用 `ComponentActivity` + `setContent` 建 `FoodMindTheme` 和 root shell；Home、Recipe Library、Cooking Plan、Groups、Explore、Me 已完成 Compose screen 迁移样板。
3. 在 mock flavor 重建 Home recommendation screen，做视觉/交互对照。
4. 建 Navigation 3 五根 back stack。
5. 按 feature 迁移 Groups/Explore/Saved/Me，再接 Cooking screens。
6. 接 repository/real API，移除 `setTimeout`/硬编码。
7. 所有正式 screen 覆盖后删除 913 行 XML、旧 styles/drawable 中无引用项和 AppCompat 依赖。

过渡期可以短暂共存，但同一正式 screen 不维护两份业务逻辑。

## 9. 不采用的方案

- 不把 React Android 原型放进 WebView：不满足 native Android 要求、平台无障碍和架构规范。
- 不长期维护 XML 与 Compose 两套 UI：增加状态/视觉分叉。
- 不先做大型多模块 Clean Architecture：四周项目先证明边界和垂直切片。
- 不用 Room 复制后端业务库：MVP source of truth 是 Spring Boot/PostgreSQL；只在确认离线需求后增加窄 cache。
