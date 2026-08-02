# Android 产品、导航与屏幕规格

## 1. 根信息架构

手机使用 Material 3 NavigationBar，宽屏改为 NavigationRail；五个根目的地拥有各自 back stack：

| 根目的地 | Nav key | 主要工作 |
| --- | --- | --- |
| 首页 | `Home(mode)` | 默认推荐；切换外食/烹饪。 |
| 群组 | `Groups` | 群组、成员、feed、分享。 |
| 发现 | `Explore` | 授权图片内容。 |
| 收藏 | `Saved(section)` | Want to Try、我的菜谱。 |
| 我的 | `Me` | 资料、偏好、Dashboard、周报。 |

采用 Navigation 3 的类型化 key 和 stable multiple-back-stack 模式。进程重建后应恢复根 back stack 与安全的 screen ID，不恢复 token、完整 DTO 或敏感自由文本。

## 2. Cooking 演示底栏映射

浏览器原型的四项底栏不作为生产根导航，按下表吸收到正式产品：

| 原型入口 | 生产入口 |
| --- | --- |
| 做饭 | `Home(Cooking)` → `CookingSelect` |
| 菜谱 | `Saved(Recipes)` |
| 计划 | `CookingPlan(planId)`；可从首页最近计划进入 |
| 我的 | `Me` → Cooking/Kitchen preferences section |

这样保留烹饪交互，同时不丢失 Proposal 要求的推荐、群组、发现和收藏能力。

## 3. 目标导航图

```text
AuthGraph
├─ Login
└─ Register

MainGraph (5 top-level back stacks)
├─ Home
│  ├─ RecommendationForm
│  ├─ RecommendationResult(sessionId)
│  ├─ CookingSelect
│  └─ CookingPlan(planId)
├─ Groups
│  └─ GroupDetail(groupId)
├─ Explore
│  └─ ContentDetail(type, id)
├─ Saved
│  ├─ WantToTry
│  ├─ RecipeList
│  └─ RecipeEditor(recipeId?)
└─ Me
   ├─ Preferences
   ├─ Dashboard
   └─ WeeklyRecap(weekStart)

Overlay/secondary
├─ RecordEditor(type, id?)
└─ Chat(sessionId?)
```

Nav key 只包含标量 ID、enum 和必要日期；大型对象从 repository 重新加载。

## 4. 首页：推荐模式

- 默认 selected mode 为“外食与外卖”；切换保存在可恢复 UI state，不改服务端偏好。
- 表单发送当前上下文；历史、偏好、群组 evidence 由后端授权解析。
- generate 使用稳定 `Idempotency-Key`，pending 时禁用重复触发。
- 初始只展示 ordered candidates 的第一项；“换一个”只改变本地 index。
- accept/reject 是独立请求；后端失败时保留拒绝原因。
- reason、candidate type、fallback/model metadata 完全来自后端。

状态：`Idle`、`Editing`、`Submitting`、`Result`、`NoValidCandidate`、`Failure`。

## 5. CookingSelect

吸收 Pixel 10 原型的已验证交互：

- 18dp 页面水平边距、搜索框、横向分类 chip、双列菜谱卡；
- 选择标记、1..N 选择、底部 selection dock；
- dock 位于系统/根 NavigationBar 上方，内容具有相应 bottom inset；
- 卡片显示图片、菜名、时长、份数/工具摘要；
- 生成前允许设置目标份数、时间上限、用餐时间和可用厨房资源（以最终契约为准）。

“添加菜谱”不出现在本屏，只能从 `Saved(Recipes)` 进入。

## 6. RecipeList / RecipeEditor

RecipeList：搜索/分类、添加 FAB/顶部 action、编辑/删除菜单、空状态。RecipeEditor 使用 modal bottom sheet 或完整 destination，取决于字段长度；长食材/步骤表单优先完整 destination。

当前后端没有 `/api/v1/recipes`。Android 与 Web 一致，将菜谱草稿按账号保存在设备上；生成计划时缩放食材行并调用受支持的 `/cooking-plans/generate`，不把本地草稿称为后端持久化。

图片选择使用系统 Photo Picker，不申请不必要的广泛存储权限；上传走 media 两阶段生命周期。

## 7. CookingPlan

四个服务端 terminal 状态分别有独立 UI：

| 状态 | UI |
| --- | --- |
| `READY` | verifier success、总时长、进度、timeline、dish/resource/warning、开始/继续做饭。 |
| `NEEDS_CONFIRMATION` | 待确认假设/缺口和 repair option；确认前不可执行。 |
| `INFEASIBLE` | 约束冲突、最小可行提示和服务端 repair option。 |
| `FAILED` | 安全失败文案、重试条件、correlation ID。 |

timeline：

- DOM 对应的 Compose semantics 顺序等于执行顺序；
- active/passive、菜品和资源同时用文字/图标区分；
- 勾选进度在本地 screen/session state；后端未提供完成 API 前不能声称库存已扣减；
- 进程重建可恢复已打开 plan ID，执行勾选是否持久化需单独产品决策。

## 8. 群组、发现、收藏、我的

- 群组：服务端决定 owner/member 权限；离组/移除后清相关 repository cache 并返回安全页面。
- 发现：Lazy grid/list + 分页，只显示 `/explore` 授权内容；不能扩展为公开关注 feed。
- 收藏：Want to Try 使用公开契约；recipes 为按账号隔离的设备本地草稿，并明确标注不在后端持久化。
- 我的：资料、偏好、Dashboard、周报；指标不在设备端重算。

## 9. Chatbot

- 独立 destination 或 app-level sheet，不嵌入推荐/Cooking graph。
- 消息、session、references 独立加载；引用可点击进入授权内容。
- 失权引用显示不可用，不泄露原标题/摘要。
- 不把模型自由文本当导航指令或 executable action。

## 10. Back、deep link 与离线

- 系统 Back 遵循当前根 back stack；再次返回根页才退出，不自定义反直觉行为。
- deep link 先经过 AuthGraph；登录后回到经过类型校验的内部 nav key。
- 无网络时保留已有只读内容和明确离线条；不离线排队 AI generate/feedback 等命令。
- network 恢复不自动重复非幂等 mutation。

## 11. 文案

- 默认中文，放入 `strings.xml`，Composable 不散落硬编码可见文本。
- 使用 quantity/plural 和 format resource；金额/日期按 locale。
- 不声称检查厨房、保证卫生或认证餐厅安全。
- TalkBack 文案表达动作和状态，不复述所有视觉装饰。
