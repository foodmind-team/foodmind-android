# Compose 设计系统、自适应与无障碍

## 1. 品牌 token

以当前 Android `colors.xml` 和两端原型共同色板为起点：

| 语义 | 颜色 |
| --- | --- |
| Ink | `#17241D` |
| Muted | `#657269` |
| Paper | `#F7F9F4` / warm section `#F7F5EF` |
| Surface | `#FFFEFA` |
| Green 950 | `#113E2C` |
| Green 700 | `#287354` |
| Green 100 | `#DCEFE4` |
| Lime | `#D9EF74` |
| Coral | `#F17A5B` |
| Line | `#DFE6DF` |

在 `FoodMindColorScheme` 里映射 Material 3 semantic roles；业务 Composable 不硬编码 hex。dynamic color 默认关闭，避免品牌/状态对比被系统色改变；若未来开启需设计评审。

## 2. Shape、spacing、elevation

- spacing：4/8/12/16/18/24/32dp；手机页面水平基线 18dp。
- field 高 50–56dp；chip 38–40dp；所有 interactive target 至少 48×48dp。
- card radius 16–20dp；sheet 顶部 24–28dp；chip full radius。
- elevation 低且克制；selection 同时用 border/check/semantics。

## 3. Typography

- 使用 platform Roboto + 中文系统 fallback，不从网络下载字体。
- `display/headline/title/body/label` 由 Material typography 语义封装；避免每屏任意字号。
- 支持系统 fontScale 至少 200%；不截断主要 CTA、错误和时间线指令。
- 数字/金额/时间用 locale formatter；不在 Composable 手拼。

## 4. Adaptive layout

- compact：NavigationBar、单列详情、Cooking 双列卡只在最小卡宽满足时使用。
- medium/expanded：NavigationRail；Groups/Recipes 可 list-detail；Dashboard 增加栏数。
- 使用 WindowSizeClass/Material adaptive 能力，不判断具体 Pixel 型号。
- edge-to-edge + `WindowInsets.safeDrawing/ime/navigationBars`；fixed dock 不遮挡 IME、根导航或 gesture area。
- 旋转、分屏、fold posture 改变后保留 screen ID、选择和滚动位置。

## 5. 核心 Compose 组件

### FoodMindRecipeCard

- 整卡 `selectable`/明确 role 和 selected state；内部菜单独立可聚焦。
- `AsyncImage` 给真实菜图 content description；纯背景图置 null。
- 长中文菜名可两行；TalkBack 读取完整名称、时长和选择状态。

### FoodMindSelectionDock

- 显示已选数量和明确“生成计划”；
- pending 时按钮 disabled 并提供 progress semantics；
- 用 insets 确保不遮挡内容；横屏/大字体可换行。

### FoodMindTimeline

- `LazyColumn` 视觉顺序等于执行顺序；
- 每项语义包含开始/结束时间、菜品、active/passive、资源、完成状态；
- checkbox/按钮 target ≥48dp；不能只有彩色线表示状态；
- 总进度用文字和 progress semantics。

### FoodMindChart

- Vico 负责绘制，wrapper 同时提供标题、范围、单位、内容描述和文本摘要；
- tooltip 不是唯一读取渠道；必要时有“查看数据表”。

## 6. TalkBack 与 semantics

- 优先 Material 组件的默认 semantics，不无差别 `clearAndSetSemantics`。
- icon-only action 必须 content description；装饰 icon 置 null 避免重复。
- card 作为整体时合并相关文本，但保留内部独立按钮。
- 错误出现后通过 state 更新和 focus requester 把焦点移到错误摘要/首个字段；避免重复播报。
- snackbar/action 文案清晰；阻塞错误不只靠短暂 snackbar，应留在 screen state。
- 自定义 traversal order 只在视觉顺序与布局结构确实不一致时使用。

参考：[Compose Accessibility](https://developer.android.com/develop/ui/compose/accessibility) 与 [48dp touch target](https://developer.android.com/develop/ui/compose/accessibility/api-defaults)。

## 7. 输入、键盘与系统行为

- 正确 keyboardOptions、IME action、autofill semantics；密码不能复制到日志。
- Photo Picker 代替广泛媒体权限；被拒/取消是正常状态。
- Back 先关闭 sheet/dialog，再回 destination；predictive back 行为随 Navigation 3 stable 实现。
- destructive dialog 默认焦点不放在确认；明确对象名和不可逆后果。
- 遵守 reduced motion/系统动画设置；不伪造 AI 精确进度。

## 8. 设计 QA 设备矩阵

至少验证：

- Pixel 10/接近 390dp compact；
- 360dp 小屏、API 24；
- 7–8 inch medium；
- tablet/expanded；
- portrait/landscape、gesture/3-button nav；
- fontScale 1.0/1.3/2.0；
- light theme（MVP），dark theme 若启用；
- TalkBack、Switch Access/键盘可达性检查。

每个重要 screen 保存 populated、loading、empty、error、fallback、长文案截图，并说明与批准原型的差异。
