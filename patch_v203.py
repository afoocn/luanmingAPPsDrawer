# -*- coding: utf-8 -*-
import io, sys

ROOT = "C:/Users/CHONGJING/WorkBuddy/2026-07-08-10-17-37/CollectionDrawerNEW"

def read(p):
    with io.open(p, "r", encoding="utf-8") as f:
        return f.read()

def write(p, s):
    with io.open(p, "w", encoding="utf-8") as f:
        f.write(s)

def rep(content, old, new, count=1, label=""):
    n = content.count(old)
    if n != count:
        raise SystemExit(f"[FAIL] {label}: expected {count} occurrence(s), found {n}\n--- old snippet ---\n{old[:200]}")
    return content.replace(old, new, count)

# ---------- 1) themes.xml : Translucent style -> floating ----------
p = ROOT + "/app/src/main/res/values/themes.xml"
s = read(p)
old = '''    <style name="Theme.CollectionDrawer.Translucent" parent="Theme.CollectionDrawer">
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
new = '''    <style name="Theme.CollectionDrawer.Translucent" parent="Theme.CollectionDrawer">
        <!-- 浮动窗口：窗口只覆盖卡片大小（宽度=屏宽-2*边距，高度=内容自适应），
             状态栏区域保持宿主（MainActivity / 桌面）的，本窗口不重绘 -> 不闪。
             window.setBackgroundBlurRadius 只磨砂「窗口背后那块」=卡片，屏幕其余正常显示。 -->
        <item name="android:windowIsFloating">true</item>
        <!-- @null 彻底移除 windowBackground，避免启动瞬间白色闪烁 -->
        <item name="android:windowBackground">@null</item>
        <item name="android:windowNoTitle">true</item>
        <!-- 卡片外完全透明（点外面关闭用 FLAG_WATCH_OUTSIDE_TOUCH），不加系统暗色遮罩 -->
        <item name="android:backgroundDimEnabled">false</item>
        <!-- 禁用系统窗口动画：只有卡片自身做 view-level 动画 -->
        <item name="android:windowAnimationStyle">@null</item>
        <!-- 禁用窗口预览（starting window），避免启动时闪屏 -->
        <item name="android:windowDisablePreview">true</item>
        <!-- 禁用 Android 12+ SplashScreen -->
        <item name="android:windowSplashScreenBackground" tools:targetApi="s">@android:color/transparent</item>
        <item name="android:windowSplashScreenAnimatedIcon" tools:targetApi="s">@null</item>
        <item name="android:windowSplashScreenAnimationDuration" tools:targetApi="s">0</item>
    </style>'''
s = rep(s, old, new, 1, "themes Translucent->floating")
write(p, s)
print("[OK] themes.xml -> floating window")

# ---------- 2) AndroidManifest.xml : dialog launchMode + remove taskAffinity ----------
p = ROOT + "/app/src/main/AndroidManifest.xml"
s = read(p)
old = '''        <!-- 快速启动弹窗（桌面快捷方式点击时弹出的简洁卡片） -->
        <activity
            android:name=".QuickLaunchDialogActivity"
            android:exported="false"
            android:taskAffinity=""
            android:launchMode="singleInstance"
            android:excludeFromRecents="true"
            android:noHistory="true"
            android:theme="@style/Theme.CollectionDrawer.Translucent" />'''
new = '''        <!-- 快速启动弹窗（桌面快捷方式 / 应用内点击时弹出的简洁卡片）
             标准启动模式 + 继承应用 taskAffinity：从应用内启动时复用宿主 task，
             状态栏不重绘 -> 不闪；从桌面快捷方式启动仍正常（背后是桌面）。 -->
        <activity
            android:name=".QuickLaunchDialogActivity"
            android:exported="false"
            android:launchMode="standard"
            android:excludeFromRecents="true"
            android:noHistory="true"
            android:theme="@style/Theme.CollectionDrawer.Translucent" />'''
s = rep(s, old, new, 1, "manifest dialog launchMode")
write(p, s)
print("[OK] AndroidManifest.xml -> standard launchMode, no taskAffinity")

# ---------- 3) QuickLaunchDialogActivity.kt ----------
p = ROOT + "/app/src/main/java/com/fengnian/folderdrawer/QuickLaunchDialogActivity.kt"
s = read(p)

# 3a) add WindowManager import
old = "import android.view.ViewGroup\n"
new = "import android.view.ViewGroup\nimport android.view.WindowManager\n"
s = rep(s, old, new, 1, "import WindowManager")

# 3b) onCreate: add FLAG_WATCH_OUTSIDE_TOUCH
old = '''        // 全屏半透明窗口：窗口铺满整屏但背景透明，露出背后内容。
        // 状态栏随之透出背后任务，因此无论从左面 Activity 还是桌面快捷方式启动都不会重绘/闪烁。
        window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))'''
new = '''        // 浮动窗口：窗口只覆盖卡片大小，状态栏由宿主（MainActivity/桌面）绘制，本窗口不重绘 -> 不闪。
        window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        // 捕获窗口外的触摸（MotionEvent.ACTION_OUTSIDE）用于「点外面关闭」
        window?.addFlags(WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH)'''
s = rep(s, old, new, 1, "onCreate outside-touch flag")

# 3c) applyDialogLayout -> floating window sizing
old = '''    private fun applyDialogLayout(_collection: Collection) {
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
    }'''
new = '''    private fun applyDialogLayout(_collection: Collection) {
        val density = resources.displayMetrics.density
        val screenW = resources.displayMetrics.widthPixels

        // 浮动窗口：宽度 = 屏宽 - 2*边距（即「距屏幕边缘最小值」），高度由内容 wrap_content 自适应
        val marginPx = (DialogSettings.getMarginHorizontal(this) * density).toInt()
        val width = screenW - 2 * marginPx

        val position = DialogSettings.getPosition(this)
        val attrs = window?.attributes
        if (attrs != null) {
            if (position == "bottom") {
                attrs.gravity = Gravity.BOTTOM
                // 贴底时上移一个导航栏高度，避免被系统导航条遮挡
                val navH = getNavigationBarHeight()
                attrs.y = ((DialogSettings.getBottomMargin(this) + 8) * density).toInt() + navH
            } else {
                attrs.gravity = Gravity.CENTER
                attrs.y = 0
            }
            window?.attributes = attrs
        }
        // 浮动窗口本身即卡片大小：窗口宽=屏宽-2*边距，高=内容自适应
        window?.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT)

        // 卡片填满浮动窗口，不再用左右边距
        val cparams = binding.contentCard.layoutParams as FrameLayout.LayoutParams
        cparams.marginStart = 0
        cparams.marginEnd = 0
        cparams.bottomMargin = 0
        cparams.gravity = Gravity.CENTER
        binding.contentCard.layoutParams = cparams
    }'''
s = rep(s, old, new, 1, "applyDialogLayout floating")

# 3d) dispatchTouchEvent -> handle ACTION_OUTSIDE
old = '''    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        // 左右滑切换标签页的手势识别（手指落在卡片内也能识别）
        tabGestureDetector?.onTouchEvent(ev)
        return super.dispatchTouchEvent(ev)
    }'''
new = '''    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        // 点窗口外任意区域 -> 关闭弹窗（浮动窗口用 FLAG_WATCH_OUTSIDE_TOUCH 捕获 ACTION_OUTSIDE）
        if (ev.action == MotionEvent.ACTION_OUTSIDE) {
            dismissWithAnimation()
            return true
        }
        // 左右滑切换标签页的手势识别（手指落在卡片内也能识别）
        tabGestureDetector?.onTouchEvent(ev)
        return super.dispatchTouchEvent(ev)
    }'''
s = rep(s, old, new, 1, "dispatchTouchEvent ACTION_OUTSIDE")

# 3e) applyCardBackground non-blur branch -> root transparent (no full-screen dim)
old = '''            // 无原生模糊（Android 11 或模糊度=0）时，整屏半透明窗口外区域加一层轻暗化，
            // 让弹窗在视觉上从背后内容中分离出来（iOS 在无模糊时也是暗化背景）
            binding.root.setBackgroundColor(0x33000000)'''
new = '''            // 卡片外保持透明：背后内容正常显示（不磨砂、不暗化），仅卡片本身有颜色
            binding.root.setBackgroundColor(Color.TRANSPARENT)'''
s = rep(s, old, new, 1, "non-blur root transparent")

write(p, s)
print("[OK] QuickLaunchDialogActivity.kt patched (5 edits)")

# ---------- 4) build.gradle.kts : version 84/2.02 -> 85/2.03 ----------
p = ROOT + "/app/build.gradle.kts"
s = read(p)
s = rep(s, "versionCode = 84", "versionCode = 85", 1, "versionCode")
s = rep(s, 'versionName = "2.02"', 'versionName = "2.03"', 1, "versionName")
write(p, s)
print("[OK] build.gradle.kts -> 85 / 2.03")

print("\nALL PATCHES APPLIED.")
