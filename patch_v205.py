import io, sys

BASE = "C:/Users/CHONGJING/WorkBuddy/2026-07-08-10-17-37/CollectionDrawerNEW"
LAYOUT = BASE + "/app/src/main/res/layout/activity_quick_launch_dialog.xml"
ACT = BASE + "/app/src/main/java/com/fengnian/folderdrawer/QuickLaunchDialogActivity.kt"
GRADLE = BASE + "/app/build.gradle.kts"

def read(p):
    with open(p, "r", encoding="utf-8") as f:
        return f.read()

def write(p, s):
    with open(p, "w", encoding="utf-8") as f:
        f.write(s)

def replace_once(s, old, new, label):
    cnt = s.count(old)
    if cnt != 1:
        raise SystemExit(f"[FAIL] {label}: expected 1 match, found {cnt}\n---OLD---\n{old}\n---")
    print(f"[OK] {label}")
    return s.replace(old, new)

# ---------- 1) 布局：根布局高度 wrap_content -> match_parent（铺满卡片大小的窗口，透明） ----------
lay = read(LAYOUT)
lay = replace_once(lay,
    '    android:layout_width="match_parent"\n'
    '    android:layout_height="wrap_content"\n'
    '    android:background="#00000000">',
    '    android:layout_width="match_parent"\n'
    '    android:layout_height="match_parent"\n'
    '    android:background="#00000000">',
    "layout root height match_parent")
write(LAYOUT, lay)

# ---------- 2) applyDialogLayout：去掉失效的 WRAP_CONTENT 高度，改为限制网格最大高度 + 临时占位高度 ----------
act = read(ACT)
act = replace_once(act,
    '        // 浮动窗口：宽度 = 屏宽 - 2*边距（即「距屏幕边缘最小值」），高度由内容 wrap_content 自适应\n'
    '        val marginPx = (DialogSettings.getMarginHorizontal(this) * density).toInt()\n'
    '        val width = screenW - 2 * marginPx\n'
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
    '        // 浮动窗口本身即卡片大小：窗口宽=屏宽-2*边距，高=内容自适应\n'
    '        window?.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT)',
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
    '        // 限制网格最大高度（超过则网格内部滚动），从根源避免弹窗占满全屏\n'
    '        val maxRecyclerH = ((screenH * 0.72f - 120f * density)).toInt()\n'
    '            .coerceAtLeast((screenH * 0.3f).toInt())\n'
    '        binding.appsRecycler.maxHeight = maxRecyclerH\n'
    '        binding.appsRecyclerB.maxHeight = maxRecyclerH\n'
    '\n'
    '        // 先给一个临时高度占位，数据加载/切换完成后由 updateWindowHeight() 调整为卡片真实高度（紧凑自适应）\n'
    '        window?.setLayout(width, (screenH * 0.5f).toInt())',
    "applyDialogLayout: drop WRAP_CONTENT height, add recycler maxHeight + placeholder")

# ---------- 3) 新增 updateWindowHeight() 方法（在 getNavigationBarHeight 之前） ----------
act = replace_once(act,
    '    /** 读取系统导航栏高度（无导航栏时返回 0） */\n'
    '    private fun getNavigationBarHeight(): Int {',
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
    '\n'
    '        var h = forceHeight\n'
    '        if (h <= 0) {\n'
    '            val wSpec = View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY)\n'
    '            val hSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)\n'
    '            binding.contentCard.measure(wSpec, hSpec)\n'
    '            h = binding.contentCard.measuredHeight\n'
    '        }\n'
    '        val maxH = (screenH * 0.85f).toInt()\n'
    '        if (h > maxH) h = maxH\n'
    '        if (h < 1) h = (screenH * 0.5f).toInt()\n'
    '        window?.setLayout(width, h)\n'
    '    }\n'
    '\n'
    '    /** 读取系统导航栏高度（无导航栏时返回 0） */\n'
    '    private fun getNavigationBarHeight(): Int {',
    "add updateWindowHeight() method")

# ---------- 4) 初始数据加载完成后校准窗口高度 ----------
act = replace_once(act,
    '        lifecycleScope.launch {\n'
    '            val apps = withContext(Dispatchers.IO) {\n'
    '                viewModel.repository.getApps(initialCollection.id)\n'
    '            }\n'
    '            initialAdapter.submitList(apps)\n'
    '        }',
    '        lifecycleScope.launch {\n'
    '            val apps = withContext(Dispatchers.IO) {\n'
    '                viewModel.repository.getApps(initialCollection.id)\n'
    '            }\n'
    '            initialAdapter.submitList(apps)\n'
    '            // 数据加载完成后，按真实内容高度把弹窗收紧为卡片大小（紧凑自适应）\n'
    '            binding.appsRecycler.postDelayed({ updateWindowHeight() }, 120)\n'
    '        }',
    "initial load: post updateWindowHeight")

# ---------- 5) 切换标签前：预设窗口高度为目标卡片高度 ----------
act = replace_once(act,
    '        // 旧层朝手势方向滑出淡出',
    '        // 切换前先把窗口高度预设为目标卡片高度，避免动画过程中弹窗撑满/错位\n'
    '        if (targetH > 0) updateWindowHeight(targetH)\n'
    '\n'
    '        // 旧层朝手势方向滑出淡出',
    "switchToTab: pre-set window height")

# ---------- 6) 切换动画结束：重新校准窗口高度 ----------
act = replace_once(act,
    '                doOnEnd {\n'
    '                    container.layoutParams.height = ViewGroup.LayoutParams.WRAP_CONTENT\n'
    '                    container.requestLayout()\n'
    '                }',
    '                doOnEnd {\n'
    '                    container.layoutParams.height = ViewGroup.LayoutParams.WRAP_CONTENT\n'
    '                    container.requestLayout()\n'
    '                    // 动画结束后按新内容重新校准窗口高度\n'
    '                    updateWindowHeight()\n'
    '                }',
    "switchToTab doOnEnd: recalc height")

# ---------- 7) 入场动画结束后兜底校准 ----------
act = replace_once(act,
    '        playEnterAnimation(initialCollection)',
    '        playEnterAnimation(initialCollection)\n'
    '        // 入场动画结束后再次校准窗口高度（兜底，确保紧凑）\n'
    '        binding.root.postDelayed({ updateWindowHeight() }, 260)',
    "after enter animation: recalc height")

write(ACT, act)

# ---------- 8) 版本号 86/2.04 -> 87/2.05 ----------
g = read(GRADLE)
g = replace_once(g, 'versionCode = 86', 'versionCode = 87', "versionCode 87")
g = replace_once(g, 'versionName = "2.04"', 'versionName = "2.05"', "versionName 2.05")
write(GRADLE, g)

print("\nALL PATCHES APPLIED.")
