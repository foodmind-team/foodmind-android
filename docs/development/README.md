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

## 当前实现

- 原生 Compose 客户端覆盖 Web 的认证、推荐、记录、群组、发现、收藏、烹饪、聊天、洞察、资料与偏好流程。
- [Web parity implementation](../web-parity-implementation.md) 是功能/API 映射的当前事实来源。
- [Xiaohongshu reference adaptation](../ux/xiaohongshu-adaptation.md) 记录视觉参考如何在保留 FoodMind 品牌的前提下落地。
- 后端没有 `/recipes` 契约；菜谱草稿按账号保存在设备上，生成计划时只发送受支持的食材结构。

## 开发者开始前检查

- compileSdk 37、targetSdk 36、JDK 17 与 Compose 编译插件已配置；`core/network` 覆盖当前 Spring `/api/v1` 公共契约，并由 MockWebServer 验证关键 header、刷新与并发版本行为。
- 只采用 stable Compose BOM 与 Navigation 3 release；不跟随 sample `main` 的 alpha/snapshot。
- Contract Gate C-01 未关闭前不把 OpenAPI Generator 直接接入主构建。
- `MainActivity` 只做单 Activity 容器；screen/network/state 不回填到 Activity。
- UI 只访问 ViewModel；ViewModel 只访问 use case/repository；客户端只访问 Spring Boot。
- 原型图片和远程 URL 未确认许可前不得进入 release。
