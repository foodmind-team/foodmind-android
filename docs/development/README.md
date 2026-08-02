# FoodMind Android 生产开发文档

> 目标仓库：`foodmind-android`
> 当前基线：AGP 9.2.1、compileSdk 37、targetSdk 36、minSdk 24、JDK 17；Gradle/lint 基线已恢复通过
> 目标：原生 Kotlin + Jetpack Compose；不采用 WebView/React Native 包装原型

## 文档集

1. [产品、导航与屏幕规格](./01-product-navigation-and-screens.md)
2. [架构、技术栈与迁移](./02-architecture-stack-and-migration.md)
3. [网络、状态、错误与会话](./03-network-state-errors-and-session.md)
4. [Compose 设计系统、自适应与无障碍](./04-compose-design-adaptive-and-a11y.md)
5. [测试、安全、性能与交付](./05-testing-security-performance-and-delivery.md)

共同前置材料：

- [仓库与原型审计](../../../docs/client-development/00-repository-audit.md)
- [GitHub 成熟方案与复用登记](../../../docs/client-development/01-research-and-reuse-register.md)
- [跨仓库契约门槛](../../../docs/client-development/02-contract-gates.md)
- [跨端一致性与共享 UAT](../../../docs/client-development/03-cross-client-parity.md)
- [最终实施计划](../../../docs/client-development/04-implementation-plan.md)

## 两个 Android “原型”的关系

- 正式仓库中的 XML/AppCompat 页面是真正原生，但只是 recommendation-first 静态原型。
- 当前主要页面已迁移为 Compose：启动首页、我的菜谱、菜谱编辑、烹饪计划、历史记录、发现、群组列表、群组动态、数据看板、FoodMind 助手、我的、登录页均共享 `FoodMindTheme`。Cooking 页面支持 `READY / NEEDS_CONFIRMATION / INFEASIBLE / FAILED` 四种 fixture 状态；非 READY 不展示可执行步骤。历史记录通过 `/api/v1/history` 加载近 30 天数据，发现通过 `/api/v1/explore` 使用 cursor 分页，群组通过 `/api/v1/groups` 与 `/groups/{groupId}/feed` 加载列表和动态，数据看板通过 owner-scoped `/api/v1/dashboard` 加载近 30 天指标，助手通过 `/api/v1/chat/sessions` 与 `/messages` 创建会话并发送消息；页面均有 loading/error/empty 状态与分页入口。菜谱编辑与选择页已接入 `/api/v1/recipes` 的 C-08 adapter（list/detail/create/update/delete、If-Match 版本），没有登录环境时保留明确标记的本地草稿 fallback。状态由 `HomeViewModel`、`RecipeSelectionViewModel`、`CookingPlanViewModel`、`AuthViewModel`、`HistoryViewModel`、`ExploreViewModel`、`GroupsViewModel`、`GroupFeedViewModel`、`DashboardViewModel`、`ChatViewModel` + `StateFlow` 管理，并有 JVM 单测覆盖页面状态与 API 契约；旧 XML 仍作为未迁移内容的回退。
- `output/prototype/foodmind-android` 是浏览器里的 Pixel 10 演示，提供中文 Cooking 流程、尺寸、间距和 QA 证据，不是可提交的 Android 代码。
- 生产实现用 Compose 重建两者：保留正式全产品根导航，吸收 Cooking 的选菜/菜谱/计划交互。

## 开发者开始前检查

- compileSdk 37、targetSdk 36、JDK 17 与 Compose 编译插件已完成；启动首页、菜谱库、Cooking Plan 和 Section shell 已 Compose 化，`core/network` 已接 Retrofit/OkHttp 的 Spring `/api/v1` 边界与 MockWebServer 契约测试；OpenAPI Generator 7.24.0 临时生成 spike（Kotlin Retrofit）已通过，Keystore session 恢复、C-08 recipe adapter 与真实 API schema 已接入；Pixel_10_Pro API 37 AVD 的 provider/consumer smoke test 已通过。
- 只采用 stable Compose BOM 与 Navigation 3 release；不跟随 sample `main` 的 alpha/snapshot。
- Contract Gate C-01 未关闭前不把 OpenAPI Generator 直接接入主构建。
- `MainActivity` 只做单 Activity 容器；screen/network/state 不回填到 Activity。
- UI 只访问 ViewModel；ViewModel 只访问 use case/repository；客户端只访问 Spring Boot。
- 原型图片和远程 URL 未确认许可前不得进入 release。
