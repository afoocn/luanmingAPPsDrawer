BASE = "C:/Users/CHONGJING/WorkBuddy/2026-07-08-10-17-37/CollectionDrawerNEW"
ACT = BASE + "/app/src/main/java/com/fengnian/folderdrawer/QuickLaunchDialogActivity.kt"

def read(p):
    with open(p, "r", encoding="utf-8") as f:
        return f.read()

def write(p, s):
    with open(p, "w", encoding="utf-8") as f:
        f.write(s)

def replace_once(s, old, new, label):
    cnt = s.count(old)
    if cnt != 1:
        raise SystemExit(f"[FAIL] {label}: expected 1 match, found {cnt}\n---OLD---\n{old}")
    print(f"[OK] {label}")
    return s.replace(old, new)

act = read(ACT)

# ---------- A) 去掉 applyDialogLayout 里无效的 maxHeight 设置（RecyclerView 不认 maxHeight 属性） ----------
act = replace_once(act,
    '        // 限制网格最大高度（超过则网格内部滚动），从根源避免弹窗占满全屏\n'
    '        val maxRecyclerH = ((screenH * 0.72f - 120f * density)).toInt()\n'
    '            .coerceAtLeast((screenH * 0.3f).toInt())\n'
    '        binding.appsRecycler.maxHeight = maxRecyclerH\n'
    '        binding.appsRecyclerB.maxHeight = maxRecyclerH\n'
    '\n'
    '        // 先给一个临时高度占位，数据加载/切换完成后由 updateWindowHeight() 调整为卡片真实高度（紧凑自适应）\n'
    '        window?.setLayout(width, (screenH * 0.5f).toInt())',
    '        // 网格最大高度由 updateWindowHeight() 在测量后统一限制（超过则固定高度、内部滚动）\n'
    '        // 先给一个临时高度占位，数据加载/切换完成后由 updateWindowHeight() 调整为卡片真实高度（紧凑自适应）\n'
    '        window?.setLayout(width, (screenH * 0.5f).toInt())',
    "applyDialogLayout: drop invalid maxHeight")

# ---------- B) 增强 updateWindowHeight：真正限制网格高度（固定上限 + 内部滚动） ----------
old_method = (
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
)

new_method = (
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
)

act = replace_once(act, old_method, new_method, "updateWindowHeight: enforce grid height cap")

write(ACT, act)
print("\nPATCH v205b APPLIED.")
