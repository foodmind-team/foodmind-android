# Android 网络、状态、错误与会话

## 1. 网络边界

- APK 只知道 Spring Boot public base URL；不知道 Agent/inference/PostgreSQL/S3 secret。
- Retrofit service/generated API 只在 data remote 层。
- OkHttp client 由 Hilt `SingletonComponent` 提供，拦截器顺序固定并有测试。
- 所有 suspend API 对主线程安全；repository 负责 dispatcher/thread policy。

当前实现已落地最小真实边界：`core/network` 提供 Retrofit `FoodMindApi`、Gson DTO、Bearer token 拦截器、自动 `X-Correlation-ID`，以及由 recommendation/cooking use case 传入的 `Idempotency-Key`。登录页通过 `FoodMindApiClient` 调用 `/auth/login`，成功后 access token 只驻留内存、refresh token 由 `KeystoreSessionTokenStore` 加密保存，并支持 refresh token rotation；退出登录调用 `/auth/logout`（网络失败仍清理本地会话），并有 MockWebServer 契约测试。推荐页已通过 `RecommendationRepository` 接入真实响应，菜谱库提供“调用真实 API 生成”入口并由 `CookingPlanRepository` 映射后端状态、步骤和 warning。客户端只拼接 Spring Boot `/api/v1/`，不会访问 Agent 或内部服务。

## 2. OkHttp 拦截器

建议顺序：

1. correlation/request metadata；
2. session access token（仅 public API）；
3. idempotency header（由 use case/request context 提供，不由 interceptor 随机重建）；当前 cooking client 每次显式生成并传入 UUID；
4. bounded authenticator/single-flight refresh；
5. debug-only redacted logging；
6. transport。

日志规则：

- release 不启用 body logging；
- debug 也要 redact `Authorization`、cookie、token、password、自由文本和 presigned URL query；
- correlation ID、status、method、path template、elapsed time 可记录；
- 不记录完整 recipe/chat/group 内容。

## 3. Timeout 与 retry

- 普通 API 使用统一 connect/read/write timeout；AI generate 可通过单独 Retrofit/OkHttp qualifier 使用更长 read/call timeout。
- GET 网络失败/5xx 最多有界重试；400/401/403/404/409/422 不重试；429 尊重 `Retry-After`。
- POST/PUT/DELETE 不自动重试。
- 带稳定 Idempotency-Key 的 generate 在结果未知时只由用户显式重试，并复用 key/payload。
- app 进后台不继续无限等待；取消 UI collection 不应破坏后端已提交的命令，回前台可按 plan/session ID 查询。

## 4. 错误映射

```kotlin
sealed interface AppFailure {
    data class Validation(val fields: Map<String, String>, val correlationId: String?) : AppFailure
    data class Authentication(val correlationId: String?) : AppFailure
    data class Forbidden(val correlationId: String?) : AppFailure
    data class NotFound(val correlationId: String?) : AppFailure
    data class Conflict(val code: String, val correlationId: String?) : AppFailure
    data class RateLimited(val retryAfterSeconds: Long?, val correlationId: String?) : AppFailure
    data class Offline(val cause: IOException) : AppFailure
    data class Timeout(val correlationId: String?) : AppFailure
    data class Upstream(val correlationId: String?) : AppFailure
    data class Unexpected(val correlationId: String?) : AppFailure
}
```

generated response/error 先在 data 层解析，再映射 domain failure。UI 不根据 HTTP message 猜测业务类型，也不显示 raw exception。

## 5. Session 设计

### 存储

- access token：内存优先；进程死亡后用 refresh 恢复。
- refresh token：当前已使用 Android Keystore 中不可导出 AES-GCM key 加密，密文与 IV 存 app-private preferences；后续可无损迁移到 DataStore schema。
- 不使用已弃用的 `EncryptedSharedPreferences`/`MasterKey` API。
- session store、密文、用户敏感 cache 从 Auto Backup 和 device transfer 中排除。

### 刷新状态机

```text
Unknown → Restoring → Authenticated
                    ↘ Anonymous
Authenticated → Refreshing(single flight) → Authenticated | Anonymous
Authenticated → LoggingOut → Anonymous
```

- OkHttp Authenticator 必须防递归，只对目标 host 和一次 401 工作。
- 并发 401 共享同一 refresh 结果；成功重放各自原请求一次。
- refresh 401/403 清密文、内存 token、私有 repository cache 和 back stack，进入 AuthGraph。
- 403 资源错误不 refresh。
- logout/logout-all 调后端并清本地；网络失败时提示服务端 session 可能未撤销。

## 6. DataStore 边界

可保存：

- 加密 refresh token envelope；
- 非敏感 UI preference（主题、最近选择的 mode）；
- 必要的 onboarding flag。

不保存：

- access token 明文；
- recommendation/cooking/chat 完整响应；
- group/feed/recipe 自由文本；
- Agent/internal URL 或服务凭证。

所有 DataStore schema 改动要有 migration/兼容测试。

## 7. State 与恢复

- `SavedStateHandle` 只存 ID、filter、selected ID set 等有界数据。
- 长列表/详情从 repository reload；后台恢复展示缓存时标明刷新状态。
- 表单可保存非敏感草稿；密码、token、presigned URL 不保存。
- process death 测试覆盖登录恢复、根导航、CookingSelect 和 plan detail。

## 8. 分页、搜索与图片

- 搜索 Flow 使用 debounce + distinctUntilChanged + flatMapLatest，取消旧请求。
- 分页优先显式 repository pager；只有后端分页稳定且列表规模需要时才引入 Paging 3。
- Coil 只允许 HTTPS 和受控 `content://`；禁用任意 `file://`/自定义 scheme 输入。
- 图片显示固定 aspect ratio/placeholder/error；上传走 Photo Picker + media 两阶段 API。

## 9. Cooking 状态

C-03 前 fake repository 必须精确表达四状态；关闭后真实 repository 使用相同 domain model。客户端不把：

- 200 + `FAILED` 当 transport success 页面；
- `NEEDS_CONFIRMATION` 当可执行 READY；
- timeout 当“无菜谱”；
- 本地勾选当服务端完成/库存扣减。

## 10. 可观测性

Crash/analytics event 仅包含：app version、release SHA、screen name、stable error code、correlation ID、网络类别和耗时 bucket。禁止 PII、token、自由文本、群组内容、菜谱全文和精确 presigned URL。
