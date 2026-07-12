# -*- coding: utf-8 -*-
# 2.06: 回到 2.02 全屏半透明窗口 + 全屏模糊；把所有破坏卡片测量的 window 尺寸/attributes 操作
# 全部移除，完全恢复 v1.28（设备验证可用）的干净做法：卡片仅靠 wrap_content + margin 决定大小。
import io

BASE = "C:/Users/CHONGJING/WorkBuddy/2026-07-08-10-17-37/CollectionDrawerNEW"
ACT = BASE + "/app/src/main/java/com/fengnian/folderdrawer/QuickLaunchDialogActivity.kt"
GRADLE = BASE + "/app/build.gradle.kts"

def read(p):
    with io.open(p, "r", encoding="utf-8") as f:
        return f.read()

def write(p, s):
    with io.open(p, "w", encoding="utf-8") as f:
        f.write(s)

def rep(s, old, new, label):
    n = s.count(old)
    if n != 1:
        raise SystemExit(f"[FAIL] {label}: expected 1 match, found {n}\n---OLD---\n{old[:300]}")
    print(f"[OK] {label}")
    return s.replace(old, new)

act = read(ACT)

# ---------- 1) onCreate 窗口设置：去掉 FLAG_WATCH_OUTSIDE_TOUCH，恢复 v1.28 干净做法 ----------
act = rep(act,
    '        // 浮动窗口：窗口只覆盖卡片大小，状态栏由宿主（MainActivity/桌面）绘制，本窗口不重绘 -> 不闪。\n'
    '        window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))\n'
    '        // 捕获窗口外的触摸（MotionEvent.ACTION_OUTSIDE）用于「点外面关闭」\n'
    '        window?.addFlags(WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH)',
    '        // 全屏半透明窗口（windowIsTranslucent=true）：窗口铺满整屏、背景透明，\n'
    '        // 卡片大小完全由布局（wrap_content + 左右边距）决定。\n'
    '        // 关键：绝不调用 window.setLayout / 修改 window.attributes —— 在全屏半透明窗口上\n'
    '        // 那样做会破坏卡片测量，导致卡片被撑满全屏（这正是 2.00~2.05 高度失控的根因）。\n'
    '        window?.apply {\n'
    '            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))\n'
    '            // 全屏窗口内卡片外的空白区域触摸由根布局监听处理（点卡片外关闭）\n'
    '            addFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL)\n'
    '        }',
    "onCreate window (v1.28 clean)")

# ---------- 2) applyDialogLayout：仅改卡片 margin/gravity，绝不碰 window ----------
old_layout = (
    '    private fun applyDialogLayout(_collection: Collection) {\n'
    '        val density = resources.displayMetrics.density\n'
    '        val screenW = resources.displayMetrics.widthPixels\n'
    '\n'
    '        // 窗口宽度 = 屏宽 - 2*边距（即「距屏幕边缘最小值」）；高度在数据加载/切换后由 updateWindowHeight() 精确测量为卡片真实高度\n'
    '        val marginPx = (DialogSettings.getMarginHorizontal(this) * density).toInt()\n'
    '        val width = screenW - 2 * marginPx\n'
    '        val screenH = resources.displayMetrics.heightPixels\n'
    '\n'
    '        val position = DialogSettings.getPosition(this)\n'
    '        val attrs = window?.attributes\n'
    '        if (attrs != null) {\n'
    '            if (position == "bottom") {\n'
    '                attrs.gravity = Gravity.BOTTOM\n'
    '                // 贴底时上移一个导航栏高度，避免被系统导航条遮挡\n'
    '                val navH = getNavigationBarHeight()\n'
    '                attrs.y = ((DialogSettings.getBottomMargin(this) + 8) * density).toInt() + navH\n'
    '            } else {\n'
    '                attrs.gravity = Gravity.CENTER\n'
    '                attrs.y = 0\n'
    '            }\n'
    '            window?.attributes = attrs\n'
    '        }\n'
    '\n'
    '        // 网格最大高度由 updateWindowHeight() 在测量后统一限制（超过则固定高度、内部滚动）\n'
    '        // 先给一个临时高度占位，数据加载/切换完成后由 updateWindowHeight() 调整为卡片真实高度（紧凑自适应）\n'
    '        window?.setLayout(width, (screenH * 0.5f).toInt())\n'
    '\n'
    '        // 卡片填满浮动窗口，不再用左右边距\n'
    '        val cparams = binding.contentCard.layoutParams as FrameLayout.LayoutParams\n'
    '        cparams.marginStart = 0\n'
    '        cparams.marginEnd = 0\n'
    '        cparams.bottomMargin = 0\n'
    '        cparams.gravity = Gravity.CENTER\n'
    '        binding.contentCard.layoutParams = cparams\n'
    '    }\n'
)
new_layout = (
    '    private fun applyDialogLayout(_collection: Collection) {\n'
    '        val density = resources.displayMetrics.density\n'
    '        val marginPx = (DialogSettings.getMarginHorizontal(this) * density).toInt()\n'
    '\n'
    '        // 全屏半透明窗口：卡片宽度 = 屏宽 - 2*边距（由 margin 决定），\n'
    '        // 高度 wrap_content 随 APP 数自适应；位置由 layout_gravity 决定。全程不碰 window。\n'
    '        val params = binding.contentCard.layoutParams as FrameLayout.LayoutParams\n'
    '        params.marginStart = marginPx\n'
    '        params.marginEnd = marginPx\n'
    '\n'
    '        val position = DialogSettings.getPosition(this)\n'
    '        if (position == "bottom") {\n'
    '            params.gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL\n'
    '            // 贴底时额外留出导航栏高度，避免被系统导航条遮挡\n'
    '            params.bottomMargin = (DialogSettings.getBottomMargin(this) * density).toInt() + getNavigationBarHeight()\n'
    '        } else {\n'
    '            params.gravity = Gravity.CENTER\n'
    '            params.bottomMargin = 0\n'
    '        }\n'
    '        binding.contentCard.layoutParams = params\n'
    '    }\n'
)
act = rep(act, old_layout, new_layout, "applyDialogLayout (card-only)")

# ---------- 3) 删除 updateWindowHeight() 方法（保留 getNavigationBarHeight） ----------
old_uwh = (
    '    /**\n'
    '     * 精确测量卡片（contentCard）的自然高度，并把窗口尺寸设为「宽=屏宽-2*边距，高=卡片真实高度」。\n'
    '     * 半透明窗口下 setLayout 的 WRAP_CONTENT 高度会被系统忽略（始终撑满全屏），必须用显式像素高度，\n'
    '     * 这样弹窗才能紧凑、随 APP 数量自适应；同时窗口=卡片大小，原生毛玻璃 setBackgroundBlurRadius\n'
    '     * 只作用于卡片背后（状态栏/屏幕其余区域不磨砂），正是 iOS 卡片毛玻璃效果。\n'
    '     */\n'
    '    private fun updateWindowHeight(forceHeight: Int = -1) {\n'
    '        val density = resources.displayMetrics.density\n'
    '        val screenW = resources.displayMetrics.widthPixels\n'
    '        val screenH = resources.displayMetrics.heightPixels\n'
    '        val marginPx = (DialogSettings.getMarginHorizontal(this) * density).toInt()\n'
    '        val width = screenW - 2 * marginPx\n'
    '        val maxH = (screenH * 0.85f).toInt()\n'
    '        // 网格（图标区域）最大高度：超过则固定高度、内部滚动，避免弹窗撑满全屏\n'
    '        val maxGridH = ((screenH * 0.72f - 120f * density)).toInt()\n'
    '            .coerceAtLeast((screenH * 0.3f).toInt())\n'
    '\n'
    '        // 测量网格自然高度，超过上限则给 swapContainer 设固定高度（RecyclerView 在其内 AT_MOST -> 滚动）\n'
    '        val gridWSpec = View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY)\n'
    '        val gridHSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)\n'
    '        binding.swapContainer.measure(gridWSpec, gridHSpec)\n'
    '        val gridNatural = binding.swapContainer.measuredHeight\n'
    '        val gparams = binding.swapContainer.layoutParams\n'
    '        gparams.height = if (gridNatural > maxGridH) maxGridH else ViewGroup.LayoutParams.WRAP_CONTENT\n'
    '        binding.swapContainer.layoutParams = gparams\n'
    '        binding.swapContainer.requestLayout()\n'
    '\n'
    '        var h = forceHeight\n'
    '        if (h <= 0) {\n'
    '            val wSpec = View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY)\n'
    '            val hSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)\n'
    '            binding.contentCard.measure(wSpec, hSpec)\n'
    '            h = binding.contentCard.measuredHeight\n'
    '        }\n'
    '        if (h > maxH) h = maxH\n'
    '        if (h < 1) h = (screenH * 0.5f).toInt()\n'
    '        window?.setLayout(width, h)\n'
    '    }\n'
    '\n'
    '    /** 读取系统导航栏高度（无导航栏时返回 0） */\n'
)
new_uwh = (
    '    /** 读取系统导航栏高度（无导航栏时返回 0） */\n'
)
act = rep(act, old_uwh, new_uwh, "remove updateWindowHeight()")

# ---------- 4) 初始数据加载：去掉 updateWindowHeight 调用 ----------
act = rep(act,
    '            initialAdapter.submitList(apps)\n'
    '            // 数据加载完成后，按真实内容高度把弹窗收紧为卡片大小（紧凑自适应）\n'
    '            binding.appsRecycler.postDelayed({ updateWindowHeight() }, 120)\n'
    '        }',
    '            initialAdapter.submitList(apps)\n'
    '        }',
    "remove initial updateWindowHeight call")

# ---------- 5) 入场动画后：去掉 updateWindowHeight 兜底 ----------
act = rep(act,
    '        playEnterAnimation(initialCollection)\n'
    '        // 入场动画结束后再次校准窗口高度（兜底，确保紧凑）\n'
    '        binding.root.postDelayed({ updateWindowHeight() }, 260)',
    '        playEnterAnimation(initialCollection)',
    "remove enter-anim updateWindowHeight call")

# ---------- 6) switchToTab 切换前：去掉预设窗口高度 ----------
act = rep(act,
    '        // 切换前先把窗口高度预设为目标卡片高度，避免动画过程中弹窗撑满/错位\n'
    '        if (targetH > 0) updateWindowHeight(targetH)\n'
    '\n'
    '        // 旧层朝手势方向滑出淡出',
    '        // 旧层朝手势方向滑出淡出',
    "remove switchToTab pre-set height")

# ---------- 7) switchToTab doOnEnd：去掉重新校准窗口高度 ----------
act = rep(act,
    '                doOnEnd {\n'
    '                    container.layoutParams.height = ViewGroup.LayoutParams.WRAP_CONTENT\n'
    '                    container.requestLayout()\n'
    '                    // 动画结束后按新内容重新校准窗口高度\n'
    '                    updateWindowHeight()\n'
    '                }',
    '                doOnEnd {\n'
    '                    container.layoutParams.height = ViewGroup.LayoutParams.WRAP_CONTENT\n'
    '                    container.requestLayout()\n'
    '                }',
    "remove switchToTab doOnEnd updateWindowHeight")

# ---------- 8) dispatchTouchEvent：去掉 ACTION_OUTSIDE 分支 ----------
act = rep(act,
    '    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {\n'
    '        // 点窗口外任意区域 -> 关闭弹窗（浮动窗口用 FLAG_WATCH_OUTSIDE_TOUCH 捕获 ACTION_OUTSIDE）\n'
    '        if (ev.action == MotionEvent.ACTION_OUTSIDE) {\n'
    '            dismissWithAnimation()\n'
    '            return true\n'
    '        }\n'
    '        // 左右滑切换标签页的手势识别（手指落在卡片内也能识别）\n'
    '        tabGestureDetector?.onTouchEvent(ev)\n'
    '        return super.dispatchTouchEvent(ev)\n'
    '    }',
    '    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {\n'
    '        // 左右滑切换标签页的手势识别（手指落在卡片内也能识别）\n'
    '        tabGestureDetector?.onTouchEvent(ev)\n'
    '        return super.dispatchTouchEvent(ev)\n'
    '    }',
    "dispatchTouchEvent remove ACTION_OUTSIDE")

write(ACT, act)
print("[OK] QuickLaunchDialogActivity.kt patched")

# ---------- 9) 版本号 87/2.05 -> 88/2.06 ----------
g = read(GRADLE)
g = rep(g, "versionCode = 87", "versionCode = 88", "versionCode 88")
g = rep(g, 'versionName = "2.05"', 'versionName = "2.06"', "versionName 2.06")
write(GRADLE, g)
print("[OK] build.gradle.kts -> 88 / 2.06")

print("\nALL v2.06 PATCHES APPLIED.")
