import io

BASE = "C:/Users/CHONGJING/WorkBuddy/2026-07-08-10-17-37/CollectionDrawerNEW"
THEMES = BASE + "/app/src/main/res/values/themes.xml"
QL = BASE + "/app/src/main/java/com/fengnian/folderdrawer/QuickLaunchDialogActivity.kt"
APP = BASE + "/app/src/main/java/com/fengnian/folderdrawer/AppPickerActivity.kt"
ADAPTER = BASE + "/app/src/main/java/com/fengnian/folderdrawer/adapter/InstalledAppAdapter.kt"
BUILD = BASE + "/app/build.gradle.kts"

def patch(path, old, new, label):
    with io.open(path, "r", encoding="utf-8") as f:
        data = f.read()
    cnt = data.count(old)
    if cnt != 1:
        raise SystemExit(f"[{label}] expected 1 match, found {cnt}")
    data = data.replace(old, new)
    with io.open(path, "w", encoding="utf-8") as f:
        f.write(data)
    print(f"OK  [{label}] patched {path}")

# ---------- 1. themes.xml : full-screen translucent ----------
themes_old = '''    <style name="Theme.CollectionDrawer.Translucent" parent="Theme.CollectionDrawer">
        <!-- 浮动窗口：窗口尺寸由内容/手动设定决定（而非全屏），从而使原生毛玻璃只作用于卡片区域，不再整屏磨砂。
             注意：不要同时设 android:windowIsTranslucent=true —— 它与 windowIsFloating 在部分 ROM 上冲突，
             会导致浮动尺寸被忽略、窗口回退成全屏半透明层，且从同任务底层 Activity 启动时会触发状态栏重绘闪烁。 -->
        <item name="android:windowIsFloating">true</item>
        <!-- @null 比 @android:color/transparent 更强：彻底移除 windowBackground，
             避免某些 ROM 在 activity 启动瞬间渲染白色闪烁 -->
        <item name="android:windowBackground">@null</item>
        <item name="android:windowNoTitle">true</item>
        <!-- 卡片外完全透明，不加暗色遮罩 -->
        <item name="android:backgroundDimEnabled">false</item>
        <!-- 禁用系统窗口动画：整屏不做任何动画，只有卡片自身做 view-level 动画 -->
        <item name="android:windowAnimationStyle">@null</item>
        <!-- 禁用窗口预览（starting window），避免启动时闪屏 -->
        <item name="android:windowDisablePreview">true</item>
        <!-- 状态栏/导航栏透明：浮动窗口下状态栏属于背后任务、本窗口不覆盖；此处保持透明以防个别 ROM 绘制不透明条 -->
        <item name="android:statusBarColor">@android:color/transparent</item>
        <item name="android:navigationBarColor">@android:color/transparent</item>
        <item name="android:windowLightStatusBar" tools:targetApi="m">true</item>
        <item name="android:windowLightNavigationBar" tools:targetApi="o_mr1">true</item>
        <!-- 禁用 Android 12+ SplashScreen -->
        <item name="android:windowSplashScreenBackground" tools:targetApi="s">@android:color/transparent</item>
        <item name="android:windowSplashScreenAnimatedIcon" tools:targetApi="s">@null</item>
        <item name="android:windowSplashScreenAnimationDuration" tools:targetApi="s">0</item>
    </style>'''

themes_new = '''    <style name="Theme.CollectionDrawer.Translucent" parent="Theme.CollectionDrawer">
        <!-- 全屏半透明窗口：窗口铺满整屏，但背景透明、露出背后内容。
             半透明窗口下状态栏区域直接透出背后任务（MainActivity / 桌面），
             本窗口不重绘状态栏，因此无论从应用内还是桌面快捷方式启动都不会闪烁。
             卡片的实际大小由布局（左右边距 + wrap_content）决定，不再依赖浮动窗口的尺寸机制。 -->
        <item name="android:windowIsTranslucent">true</item>
        <!-- @null 比 @android:color/transparent 更强：彻底移除 windowBackground，
             避免某些 ROM 在 activity 启动瞬间渲染白色闪烁 -->
        <item name="android:windowBackground">@null</item>
        <item name="android:windowNoTitle">true</item>
        <!-- 卡片外完全透明（点外面关闭由根布局触摸监听处理），不加系统暗色遮罩 -->
        <item name="android:backgroundDimEnabled">false</item>
        <!-- 禁用系统窗口动画：整屏不做任何动画，只有卡片自身做 view-level 动画 -->
        <item name="android:windowAnimationStyle">@null</item>
        <!-- 禁用窗口预览（starting window），避免启动时闪屏 -->
        <item name="android:windowDisablePreview">true</item>
        <!-- 状态栏/导航栏透明：半透明窗口下状态栏区域透出背后任务内容，
             本窗口不绘制不透明条，故从任何来源启动都不会闪烁 -->
        <item name="android:statusBarColor">@android:color/transparent</item>
        <item name="android:navigationBarColor">@android:color/transparent</item>
        <item name="android:windowLightStatusBar" tools:targetApi="m">true</item>
        <item name="android:windowLightNavigationBar" tools:targetApi="o_mr1">true</item>
        <!-- 禁用 Android 12+ SplashScreen -->
        <item name="android:windowSplashScreenBackground" tools:targetApi="s">@android:color/transparent</item>
        <item name="android:windowSplashScreenAnimatedIcon" tools:targetApi="s">@null</item>
        <item name="android:windowSplashScreenAnimationDuration" tools:targetApi="s">0</item>
    </style>'''
patch(THEMES, themes_old, themes_new, "themes")

# ---------- 2. QuickLaunchDialogActivity.kt : onCreate window ----------
ql_win_old = '''        // 窗口背景全透明，弹窗不干涉状态栏
        window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            // 浮动窗口：用 WATCH_OUTSIDE_TOUCH 接收窗口外的触摸（以 ACTION_OUTSIDE 送达），
            // 用于「点外面关闭」。不使用 NOT_TOUCH_MODAL，避免外面触摸穿透到背后 Activity 造成误触。
            addFlags(android.view.WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH)
            // 浮动窗口：宽度 = 屏宽 - 两边边距，高度 = 内容自适应（= APP 数量）。
            // 这样原生毛玻璃只模糊卡片背后区域，而非整屏。
            val marginPx = (DialogSettings.getMarginHorizontal(this@QuickLaunchDialogActivity)
                * resources.displayMetrics.density).toInt()
            setLayout(
                resources.displayMetrics.widthPixels - 2 * marginPx,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            setGravity(Gravity.CENTER)
        }'''
ql_win_new = '''        // 全屏半透明窗口：窗口铺满整屏但背景透明，露出背后内容。
        // 状态栏随之透出背后任务，因此无论从左面 Activity 还是桌面快捷方式启动都不会重绘/闪烁。
        window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))'''
patch(QL, ql_win_old, ql_win_new, "ql-win")

# ---------- 3. QuickLaunchDialogActivity.kt : applyDialogLayout ----------
ql_layout_old = '''    private fun applyDialogLayout(_collection: Collection) {
        val density = resources.displayMetrics.density

        val params = binding.contentCard.layoutParams as FrameLayout.LayoutParams
        // 窗口已按「屏宽 - 边距」定宽，卡片直接占满窗口宽度，不再额外留边距
        params.marginStart = 0
        params.marginEnd = 0

        val position = DialogSettings.getPosition(this)
        if (position == "bottom") {
            params.gravity = Gravity.BOTTOM
            params.bottomMargin = (DialogSettings.getBottomMargin(this) * density).toInt()
        } else {
            params.gravity = Gravity.CENTER
            params.bottomMargin = 0
        }
        binding.contentCard.layoutParams = params

        // 同步浮动窗口位置（居中 or 贴底），使毛玻璃只覆盖卡片区域
        window?.apply {
            val lp = attributes
            lp.gravity = if (position == "bottom")
                Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            else Gravity.CENTER
            attributes = lp
        }
    }'''
ql_layout_new = '''    private fun applyDialogLayout(_collection: Collection) {
        val density = resources.displayMetrics.density

        val params = binding.contentCard.layoutParams as FrameLayout.LayoutParams
        // 全屏半透明窗口下：用左右边距把卡片宽度限定为「屏宽 - 2*边距」，高度由内容(wrap_content)自适应
        val marginPx = (DialogSettings.getMarginHorizontal(this) * density).toInt()
        params.marginStart = marginPx
        params.marginEnd = marginPx

        val position = DialogSettings.getPosition(this)
        if (position == "bottom") {
            params.gravity = Gravity.BOTTOM
            // 贴底时额外留出导航栏高度，避免被系统导航条遮挡
            val navH = getNavigationBarHeight()
            params.bottomMargin = ((DialogSettings.getBottomMargin(this) + 8) * density).toInt() + navH
        } else {
            params.gravity = Gravity.CENTER
            params.bottomMargin = 0
        }
        binding.contentCard.layoutParams = params
        // 全屏窗口无需同步 window gravity：窗口已铺满整屏，卡片位置由 layout_gravity 决定
    }

    /** 读取系统导航栏高度（无导航栏时返回 0） */
    private fun getNavigationBarHeight(): Int {
        val id = resources.getIdentifier("navigation_bar_height", "dimen", "android")
        return if (id > 0) resources.getDimensionPixelSize(id) else 0
    }'''
patch(QL, ql_layout_old, ql_layout_new, "ql-layout")

# ---------- 4. QuickLaunchDialogActivity.kt : dispatchTouchEvent ----------
ql_disp_old = '''    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (ev.action == MotionEvent.ACTION_OUTSIDE) {
            // 浮动窗口下，窗口外的触摸以 ACTION_OUTSIDE 送达。直接关闭弹窗并消费该事件，
            // 避免穿透到背后的 Activity（如 MainActivity）造成误触。
            dismissWithAnimation()
            return true
        }
        tabGestureDetector?.onTouchEvent(ev)
        return super.dispatchTouchEvent(ev)
    }'''
ql_disp_new = '''    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        // 左右滑切换标签页的手势识别（手指落在卡片内也能识别）
        tabGestureDetector?.onTouchEvent(ev)
        return super.dispatchTouchEvent(ev)
    }'''
patch(QL, ql_disp_old, ql_disp_new, "ql-dispatch")

# ---------- 5. QuickLaunchDialogActivity.kt : applyCardBackground root bg ----------
ql_bg_on_old = '''            binding.blurBackground.visibility = View.GONE
            binding.colorOverlay.visibility = View.VISIBLE
            binding.noiseOverlay.visibility = View.VISIBLE
            binding.contentCard.setCardBackgroundColor(Color.TRANSPARENT)'''
ql_bg_on_new = '''            binding.blurBackground.visibility = View.GONE
            binding.colorOverlay.visibility = View.VISIBLE
            binding.noiseOverlay.visibility = View.VISIBLE
            binding.contentCard.setCardBackgroundColor(Color.TRANSPARENT)
            // 全屏半透明窗口：毛玻璃由 Window.setBackgroundBlurRadius 作用于整屏背后，
            // 根布局保持透明，让磨砂后的真实背景透出
            binding.root.setBackgroundColor(Color.TRANSPARENT)'''
patch(QL, ql_bg_on_old, ql_bg_on_new, "ql-bg-on")

ql_bg_off_old = '''            binding.blurBackground.visibility = View.GONE
            binding.colorOverlay.visibility = View.GONE
            binding.noiseOverlay.visibility = View.GONE
            binding.contentCard.setCardBackgroundColor(
                BackgroundHelper.applyAlphaToColor(baseColor, bgAlpha)
            )'''
ql_bg_off_new = '''            binding.blurBackground.visibility = View.GONE
            binding.colorOverlay.visibility = View.GONE
            binding.noiseOverlay.visibility = View.GONE
            binding.contentCard.setCardBackgroundColor(
                BackgroundHelper.applyAlphaToColor(baseColor, bgAlpha)
            )
            // 无原生模糊（Android 11 或模糊度=0）时，整屏半透明窗口外区域加一层轻暗化，
            // 让弹窗在视觉上从背后内容中分离出来（iOS 在无模糊时也是暗化背景）
            binding.root.setBackgroundColor(0x33000000)'''
patch(QL, ql_bg_off_old, ql_bg_off_new, "ql-bg-off")

# ---------- 6. AppPickerActivity.kt : itemAnimator = null ----------
app_anim_old = '''        binding.appRecycler.layoutManager = LinearLayoutManager(this)
        binding.appRecycler.adapter = adapter'''
app_anim_new = '''        binding.appRecycler.layoutManager = LinearLayoutManager(this)
        binding.appRecycler.adapter = adapter
        // 关闭默认 item 动画，避免筛选/重绘时整列闪烁
        binding.appRecycler.itemAnimator = null'''
patch(APP, app_anim_old, app_anim_new, "app-animator")

# ---------- 7. InstalledAppAdapter.kt : areContentsTheSame ----------
adp_old = '''            override fun areContentsTheSame(
                oldItem: InstalledApp,
                newItem: InstalledApp
            ) = false'''
adp_new = '''            override fun areContentsTheSame(
                oldItem: InstalledApp,
                newItem: InstalledApp
            ) = oldItem.packageName == newItem.packageName &&
                    oldItem.activityName == newItem.activityName'''
patch(ADAPTER, adp_old, adp_new, "adapter-contents")

# ---------- 8. build.gradle.kts : version bump ----------
bld_old = '''        versionCode = 83
        versionName = "2.01"'''
bld_new = '''        versionCode = 84
        versionName = "2.02"'''
patch(BUILD, bld_old, bld_new, "version")

print("ALL PATCHES APPLIED")
