# 峦鸣抽屉 — 更新日志

> 包名：`com.fengnian.drawer`
> 项目路径：`CollectionDrawer/`

---

## v2.1（当前版本）

**修复 9 项问题：**

1. **抽屉卡片布局恢复** — `item_collection_card.xml` 恢复 padding=20dp, textSize=20sp, button=40dp, 分割线, marginTop=16dp
2. **抽屉卡片删除功能** — `CollectionCardAdapter` menuButton 改为 PopupMenu（编辑/删除），新增 `onDeleteClick` 回调；`MainActivity` 恢复 `confirmDeleteCollection`
3. **移除图标主题菜单** — `menu_main.xml` 删除 `action_icon_pack` 项；`MainActivity` 删除对应 handler
4. **应用取消选中** — `InstalledAppAdapter` 已添加项也保持可点击，点击调用 `onToggle(app, !isAdded)` 切换状态
5. **弹窗图标居中** — `SpacingItemDecoration` 改为对称间距（left/right/top/bottom 各=半间距），RecyclerView padding 设为半列间距
6. **导出选目录** — 用 `ActivityResultContracts.CreateDocument("application/json")` 替代写固定目录，用户可选保存位置
7. **最小字号 8sp** — `nameSizeSeekBar` max=8（范围 8-16），`CollectionEditActivity` progress+8
8. **模糊效果修复** — 模糊模式下 overlay alpha 限制在 40% 以内（30-120 范围），让模糊壁纸透出，三种模糊类型均可生效
9. **颜色选择器重写** — 4 个独立 RGBA 滑块（无灰度联动），每条轨道动态渐变反映其他通道值，顶部大色块预览 + hex 代码显示，删除 12 个废弃 drawable

**文件变更：**
- `item_collection_card.xml`、`ic_more_vert.xml`（新建）、`menu_main.xml`
- `MainActivity.kt`（重写）、`CollectionCardAdapter.kt`（重写）、`InstalledAppAdapter.kt`（重写）
- `AppPickerActivity.kt`、`QuickLaunchDialogActivity.kt`
- `dialog_color_picker.xml`（重写）、`ColorPickerDialog.kt`（重写）
- `activity_collection_edit.xml`、`CollectionEditActivity.kt`、`Collection.kt`
- `build.gradle.kts` — versionCode=18, versionName=2.1.0

**APK**：`峦鸣抽屉-v2.1.apk`（6.4MB）

---

## v2.0

**图标刷新 + 多图标包 + 三点菜单 + 闪烁修复 + 精简改名**

1. **图标替换即时刷新** — 新增 `refreshAppGrid()` 方法，替换图标后立即从 DB 重新拉取列表并 `submitList`
2. **应用图标使用图标包显示** — `EditAppGridAdapter` 已优先使用 `iconPackManager.getIcon()`，切换图标主题后自动刷新
3. **多图标包选择** — `IconPackManager` 新增临时加载指定图标包能力，替换菜单列出所有已安装图标包，取消图片选择功能
4. **备份移到三点菜单** — 移除浮动按钮，Toolbar 右上角三点菜单包含导出/恢复/图标主题
5. **卡片紧凑化** — padding 20→16dp，标题 20→18sp，按钮 40→36dp，移除分割线
6. **选择应用闪烁修复** — `notifyItemChanged(pos)` 替代 `notifyDataSetChanged()`，系统图标立即占位
7. **精简代码 + 升级 v2.0 + 改名** — 删除废弃 `CollectionAdapter`、`item_collection.xml`、7 个废弃 drawable、4 个废弃方法；APP 名称改为「峦鸣抽屉」

**APK**：`峦鸣抽屉-v2.0.apk`（6.7MB）

---

## v3.15

**iOS 毛玻璃 + 无限颜色选择器 + 长按改名 + 配置导出恢复 + 长按替换图标**

1. **iOS 毛玻璃质感** — 双层 RenderEffect 链（基础模糊 + 25% 轻量模糊）+ API 33+ ColorMatrix 降饱和提亮 + 噪点纹理叠加
2. **ARGB 颜色选择器** — 4 个 SeekBar（灰度联动 RGB / R / G / B），替代原 12 色色板
3. **弹窗内长按改名** — 长按 APP 名称 → `AppNameEditDialog`（当前名 + 原名 + 输入框）
4. **底部弹出距离** — Collection 新增 `bottomMarginDp`（0-120dp），仅底部弹出时显示
5. **隐藏名称时隐藏字号** — `nameSizeGroup` 整体控制显隐
6. **"集合"→"抽屉"** — strings.xml + 所有布局 XML + 所有 Kotlin 文件
7. **保存后留在编辑页** — `save()` 只 Toast + 刷新 UI，按返回键才回主界面
8. **配置导出/恢复** — JSON 序列化全部 Collection + AppItem，主界面新增备份按钮
9. **长按 APP 图标替换** — 从 icon pack 或图片选择，复制到私有目录存储

**APK**：`丰年抽屉-v3.15.apk`（6.7MB）

---

## v3.14

**状态栏闪烁根治 + 磨砂模糊修复 + 模糊类型选择**

1. **状态栏闪烁根治** — 移除 `FLAG_LAYOUT_NO_LIMITS`，新增 `DecorFitsSystemWindows(true)`，`onWindowFocusChanged` 锁定状态栏
2. **磨砂模糊修复** — `blurBackground`/`colorOverlay` 改为 `match_parent`，`Shader.TileMode.DECAL`，模糊半径 ×4f
3. **模糊类型选择** — Collection 新增 `blurType`（高斯/径向/拉丝），ChipGroup 选择，不同 RenderEffect 参数

**APK**：`丰年抽屉-v3.14.apk`（6.6MB）

---

## v3.13

**磨砂效果改为卡片级 + MyGesture 闪现修复**

1. **卡片级磨砂** — 移除 `window.setBackgroundBlurRadius()`，卡片内新增 `blurBackground` ImageView + `colorOverlay`，RenderEffect 仅作用于 ImageView
2. **闪现修复** — XML 中 `contentCard` 设 `alpha="0"`，`playEnterAnimation` 从 0 动画到 1，无中间可见帧

**APK**：`丰年抽屉-v3.13.apk`（6.6MB）

---

## v3.12

---

## v3.11

**MyGesture 兼容 + 背景颜色选中态修复**

1. **CreateShortcutActivity** — 响应 `ACTION_CREATE_SHORTCUT`，弹出集合选择列表，返回快捷方式 Intent + 图标
2. **背景颜色选中态** — 去掉 `tag > 0` 判断（颜色值为负数），改为 `tag == selectedBgColor` 直接比较

**APK**：`丰年抽屉-v3.11.apk`（6.7MB）

---

## v3.10

**动态快捷方式发布 — 手势软件可调用集合**

1. **publishDynamicShortcuts()** — 为每个集合构建 `ShortcutInfoCompat`，`setDynamicShortcuts()` 一次性发布
2. **自动刷新** — 集合增删改后自动刷新动态快捷方式列表
3. **CollectionLauncherActivity** — 新增 `android:label`，活动选择器中显示 APP 名称

**APK**：`丰年抽屉-v3.10.apk`（6.7MB）

---

## v3.9

**Icon Pack 入口 + 颜色选中态 + 状态栏闪烁 + 动画无效**

1. **Icon Pack 入口** — 编辑页新增"图标主题"区块，点击跳转 `IconPackListActivity`，返回后刷新单例
2. **颜色选中态** — 选中描边颜色根据 luminance 自适应（深色圆 → 白色描边，浅色圆 → 深色描边）
3. **状态栏闪烁** — 移除 `installSplashScreen()`，Translucent 主题移除 `windowLightStatusBar`，新增 `windowDisablePreview=true`
4. **动画修复** — 移除 splash 后动画不再被阻塞，`card.post{}` 确保布局完成后播放

**APK**：`丰年抽屉-v3.9.apk`（6.7MB）

---

## v3.8

**文字颜色选择 + 弹窗动画选项 + 背景简化 + 图标白边 + 背景分层**

1. **状态栏闪烁** — Translucent 主题加 `statusBarColor=transparent` + `windowLightStatusBar=false`
2. **文字颜色选择** — Collection 新增 `titleTextColor`/`appNameTextColor`，编辑页新增色板（含"自动"彩虹圆 + 12 色）
3. **弹窗动画选项** — 新增 `dialogAnimDirection`（bottom/left/right/center）、`dialogAnimStyle`（slide/scale/spring）、`dialogAnimDuration`（150-500ms）
4. **背景简化 + 磨砂修复** — 移除图片背景功能，新增 `blurRadius`（0-25），`window.setBackgroundBlurRadius()`（API 31+）
5. **图标白边** — `drawableToAdaptiveBitmap` 改为透明背景，图标绘制比例 75%→85%
6. **背景分层** — `cardElevation` 从 6dp 改为 0dp

**APK**：`丰年抽屉-v3.8.apk`（6.7MB）

---

## v3.7

**SplashScreen 动画 + 黑屏 + 图标白边 + APP 名称可读性**

1. **SplashScreen 禁用** — 新增 `core-splashscreen` 依赖，`installSplashScreen()` 接管并消除 splash
2. **整屏黑屏修复** — Translucent 主题加回 `windowIsTranslucent=true`，`windowAnimationStyle=@null`，`overridePendingTransition(0,0)`
3. **图标白边修复** — 改用 `createWithAdaptiveBitmap()`，透明背景 + 图标居中 85% safe zone
4. **APP 名称可读性** — `item_home_app.xml` 加阴影，`HomeAppGridAdapter` 新增 `backgroundMode` 参数自适应文字颜色

**APK**：`丰年抽屉-v3.7.apk`（6.6MB）

---

## v3.6

**APP 灰显 + 图片圆角 + 菜单简化 + 桌面图标 + 背景文字 + 弹窗动画**

1. **APP 灰显移除** — `root.alpha` 固定为 1f
2. **图片圆角** — `cardBackground` 改为 `ShapeableImageView`，20dp 圆角
3. **菜单简化** — 三点菜单改为编辑按钮（铅笔图标），直接进入编辑
4. **桌面图标** — 全部用 `createWithBitmap`（非 adaptive），名称无"丰年抽屉 -"前缀
5. **背景文字可读** — 文字加 `shadowLayer`，根据背景模式自适应白/黑字
6. **弹窗动画** — 新增 `anim/dialog_enter.xml` + `anim/dialog_exit.xml`，view-level 动画（底部滑入 + 缩放 + 淡入）

**APK**：`丰年抽屉-v3.6.apk`（6.6MB）

---

## v3.5

**遮罩残留 + APP 选择 bug + Icon Pack 图标选择 + 背景图片填充**

1. **遮罩** — 主题 `backgroundDimEnabled` 改为 `false`，卡片外完全透明
2. **APP 选择器重写** — 移除 `selected` 集合和 checkbox listener，`alreadyAdded` 为唯一数据源
3. **Icon Pack 图标选择** — Collection 新增 `shortcutIconDrawable`，新增 `IconPickerActivity`（5 列图标网格）
4. **背景图片填充** — `scaleType` 改为 `matrix`，按宽度等比缩放 + 保留顶部

**APK**：`丰年抽屉-v3.5.apk`（6.6MB）

---

## v3.4

**弹窗背景改为卡片背景 + 修复图标不显示 + 弹窗尺寸/位置/间距/标题可配置**

1. **弹窗背景改为卡片背景** — 删除全屏遮罩，卡片内新增 `cardBackground` ImageView
2. **修复 race condition** — adapter 改为 nullable，`pendingApps` 暂存未初始化时的数据
3. **新增弹窗配置** — `dialogMarginHorizontal`、`dialogPosition`（center/bottom）、`dialogRowSpacing`、`dialogColumnSpacing`、`showDialogTitle`
4. **标题紧凑化** — padding 24→14dp，titleSize 20→16sp

**APK**：`丰年抽屉-v3.4.apk`（6.6MB）

---

## v3.3

**弹窗背景全屏化 + 编辑界面全面修复 + 弹窗可配置项 + 版本号**

1. **弹窗布局重构** — 全屏背景层 + 居中卡片，去掉关闭按钮，减小边框
2. **颜色选择器修复** — 弃用 `color_circle_selector.xml`，改用代码生成 `GradientDrawable`，选中状态 3dp 描边
3. **图片预览整体可点击** — `imagePreview` MaterialCardView 绑定点击事件
4. **透明度实时预览** — 背景卡片内动态插入预览 View
5. **弹窗样式配置** — `gridColumns`(3-8)、`iconSizeDp`(36-72)、`showAppName`、`compactAppName`、`appNameSizeSp`(9-16)

**APK**：`丰年抽屉-v3.3.apk`（6.7MB）

---

## v3.2

**弹窗带出主界面 + 编辑界面缺添减 APP + 背景设置无效**

1. **弹窗任务栈隔离** — Manifest 设 `taskAffinity=""` + `excludeFromRecents="true"` + `noHistory="true"`
2. **编辑界面增加应用管理** — 新增 `EditAppGridAdapter`（4 列网格 + 删除按钮）+ "添加应用"按钮
3. **背景设置修复** — `CollectionRepository.createCollection` 扩展接受所有背景参数
4. **删除废弃的 `HomeDialogActivity`**

**APK**：`丰年抽屉-v3.2.apk`（6.7MB）

---

## v3.1

**修正桌面点击打开的弹窗样式**

1. **新增 `QuickLaunchDialogActivity`** — 真正的"快速启动弹窗"：标题 + 5 列 app grid + 关闭按钮，无编辑按钮
2. **`CollectionLauncherActivity` 改路由** — 从 `HomeDialogActivity` 改为 `QuickLaunchDialogActivity`
3. **点击外部区域关闭** — `setOnTouchListener` + `getLocationOnScreen`

**APK**：`丰年抽屉-v3.1.apk`（6.7MB）

---

## v3.0

**参考 Shortcut Maker 重做**

1. **配色重做** — 珊瑚 + 米色（#C4704A / #F5EBE0），light/dark 双主题，Material 3 标准色系
2. **主界面改为列表卡片** — 每张卡片显示名称 + 前 5 个 app 图标 + 角标
3. **新增弹窗页** — 5 列 app grid + Edit Name + Icon Pack + Place on Home Screen
4. **背景设置系统** — Collection 加 `backgroundColor`/`backgroundImageUri`/`backgroundAlpha`/`blurEnabled`
5. **BackgroundHelper** — API 31+ 用 RenderEffect，老系统用 RenderScript 兜底
6. **AppDatabase version 2** — `fallbackToDestructiveMigration`

**APK**：`丰年抽屉-v3.0.apk`（6.4MB）

---

## 技术栈

- Kotlin + MVVM + Room + Material Design 3
- AGP 8.2.0 / Kotlin 1.9.21 / Gradle 8.5
- minSdk 21 / targetSdk 34
