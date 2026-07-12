package com.fengnian.folderdrawer

import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.app.WallpaperManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.animation.AccelerateInterpolator
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import androidx.core.animation.doOnEnd
import android.widget.FrameLayout
import androidx.activity.viewModels
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.fengnian.folderdrawer.adapter.HomeAppGridAdapter
import com.fengnian.folderdrawer.data.AppItem
import com.fengnian.folderdrawer.data.Collection
import com.fengnian.folderdrawer.databinding.ActivityQuickLaunchDialogBinding
import com.fengnian.folderdrawer.iconpack.IconPackManager
import com.fengnian.folderdrawer.util.AppNameEditDialog
import com.fengnian.folderdrawer.util.AppUtils
import com.fengnian.folderdrawer.util.AutoDismissOnLock
import com.fengnian.folderdrawer.util.BackgroundHelper
import com.fengnian.folderdrawer.viewmodel.CollectionViewModel
import com.google.android.material.tabs.TabLayout
import com.fengnian.folderdrawer.util.DialogSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 快速启动弹窗（桌面快捷方式点击时弹出）
 *
 * 特点：
 * - 卡片本身可设置背景色（纯色 + alpha + 磨砂），卡片外全透明
 * - 磨砂效果使用 Window.setBackgroundBlurRadius（Android 12+ 原生毛玻璃，零内存），Android 11 回退为壁纸模糊位图；不影响状态栏
 * - 只有标题（可隐藏）+ app 图标网格
 * - 点击 app 图标启动 APP 并关闭弹窗
 * - 点击弹窗外部任意空白处关闭弹窗
 * - 没有任何编辑按钮
 * - 动画方向/风格/时长可配置
 * - 文字颜色可自定义或自动适配
 * - 多标签页支持左右滑动手势切换
 */
class QuickLaunchDialogActivity : AppCompatActivity() {

    private lateinit var binding: ActivityQuickLaunchDialogBinding
    private val viewModel: CollectionViewModel by viewModels()
    private lateinit var iconPackManager: IconPackManager
    private var collectionId: Long = -1
    private var currentCollection: Collection? = null

    /** 弹窗中作为标签页显示的 collection 列表 */
    private var allDialogCollections: List<Collection> = emptyList()
    private var currentTabCollection: Collection? = null

    /** 当前选中的标签页索引 */
    private var currentTabIndex = 0

    /** 适配器缓存，避免切换标签时重建 */
    private val adapterCache = mutableMapOf<Int, HomeAppGridAdapter>()

    /** 缓存 wallpaper bitmap，避免每次切换标签都重新读取 */
    private var cachedWallpaperBitmap: Bitmap? = null

    /** 防止重复初始化 */
    private var tabsInitialized = false

    /** 防止动画重复播放 */
    private var hasPlayedEnterAnim = false

    /** 锁屏自动退出 */
    private lateinit var autoDismiss: AutoDismissOnLock

    /** TabLayout 选择监听器是否正在由代码触发（避免 switchToTab 和 onTabSelected 循环调用） */
    private var isProgrammaticTabSelect = false

    /** 当前正在显示的网格层（双层叠加切换用） */
    private lateinit var activeRecycler: RecyclerView

    /** 切换动画进行中标记，避免动画叠加 */
    private var isSwitching = false

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.Theme_CollectionDrawer_Translucent)
        super.onCreate(savedInstanceState)

        // 禁用系统 Activity 转场动画，只保留卡片自身的 view-level 动画
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)

        // 全屏半透明窗口（windowIsTranslucent=true）：窗口铺满整屏、背景透明，
        // 卡片大小完全由布局（wrap_content + 左右边距）决定。
        // 关键：绝不调用 window.setLayout / 修改 window.attributes —— 在全屏半透明窗口上
        // 那样做会破坏卡片测量，导致卡片被撑满全屏（这正是 2.00~2.05 高度失控的根因）。
        window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            // 全屏窗口内卡片外的空白区域触摸由根布局监听处理（点卡片外关闭）
            addFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL)
        }

        binding = ActivityQuickLaunchDialogBinding.inflate(layoutInflater)
        setContentView(binding.root)

        autoDismiss = AutoDismissOnLock(this).apply { install() }

        collectionId = intent.getLongExtra(EXTRA_COLLECTION_ID, -1)
        if (collectionId == -1L) {
            finish()
            return
        }

        iconPackManager = IconPackManager.getInstance(this)

        loadDialogCollections()

        setupDismissListeners()
        observeCollection()
    }

    /**
     * 应用弹窗入场动画（在 collection 数据加载完成后调用）
     */
    private fun playEnterAnimation(_collection: Collection) {
        if (hasPlayedEnterAnim) return
        hasPlayedEnterAnim = true

        val card = binding.contentCard
        val duration = DialogSettings.getAnimDuration(this).toLong()
        val direction = DialogSettings.getAnimDirection(this)
        val style = DialogSettings.getAnimStyle(this)
        val density = resources.displayMetrics.density

        card.post {
            card.alpha = 0f
            card.scaleX = 1f
            card.scaleY = 1f
            card.translationX = 0f
            card.translationY = 0f

            when (style) {
                "scale" -> {
                    card.scaleX = 0.85f
                    card.scaleY = 0.85f
                    card.animate()
                        .alpha(1f)
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(duration)
                        .setInterpolator(DecelerateInterpolator(2f))
                        .start()
                }
                "slide" -> {
                    when (direction) {
                        "bottom" -> card.translationY = 120f * density
                        "left" -> card.translationX = -400f * density
                        "right" -> card.translationX = 400f * density
                        "center" -> {
                            card.animate()
                                .alpha(1f)
                                .setDuration(duration)
                                .setInterpolator(DecelerateInterpolator())
                                .start()
                            return@post
                        }
                    }
                    card.animate()
                        .alpha(1f)
                        .translationX(0f)
                        .translationY(0f)
                        .setDuration(duration)
                        .setInterpolator(DecelerateInterpolator(2f))
                        .start()
                }
                "spring" -> {
                    when (direction) {
                        "bottom" -> card.translationY = 120f * density
                        "left" -> card.translationX = -400f * density
                        "right" -> card.translationX = 400f * density
                        "center" -> {
                            card.scaleX = 0.85f
                            card.scaleY = 0.85f
                            card.animate()
                                .alpha(1f)
                                .scaleX(1f)
                                .scaleY(1f)
                                .setDuration(duration)
                                .setInterpolator(OvershootInterpolator(2f))
                                .start()
                            return@post
                        }
                    }
                    card.animate()
                        .alpha(1f)
                        .translationX(0f)
                        .translationY(0f)
                        .setDuration(duration)
                        .setInterpolator(OvershootInterpolator(1.5f))
                        .start()
                }
                else -> {
                    card.translationY = 120f * density
                    card.animate()
                        .alpha(1f)
                        .translationY(0f)
                        .setDuration(duration)
                        .setInterpolator(OvershootInterpolator(1.5f))
                        .start()
                }
            }
        }
    }

    private fun showAppNameEditDialog(app: AppItem) {
        val originalName = try {
            val pm = packageManager
            val info = pm.getActivityInfo(
                android.content.ComponentName(app.packageName, app.activityClassName),
                0
            )
            info.loadLabel(pm).toString()
        } catch (e: Exception) {
            app.displayName
        }

        AppNameEditDialog.show(
            activity = this,
            currentName = app.displayName,
            originalName = originalName
        ) { newName ->
            val finalName = if (newName.isBlank()) originalName else newName
            lifecycleScope.launch {
                viewModel.repository.updateAppDisplayName(app, finalName)
            }
        }
    }

    /**
     * 创建 HomeAppGridAdapter
     */
    private fun createAdapter(collection: Collection): HomeAppGridAdapter {
        return HomeAppGridAdapter(
            context = this,
            iconPackManager = iconPackManager,
            scope = lifecycleScope,
            iconSizeDp = collection.iconSizeDp,
            showAppName = collection.showAppName,
            compactAppName = collection.compactAppName,
            appNameSizeSp = collection.appNameSizeSp,
            // 应用名颜色：优先用本抽屉独立设置，否则由 adapter 按背景自动取色
            appNameTextColor = if (collection.appNameTextColor != 0) collection.appNameTextColor
                else 0,
            backgroundMode = getBackgroundMode(),
            onAppClick = { app ->
                AppUtils.launchApp(this, app.packageName, app.activityClassName)
                dismissWithAnimation()
            },
            onAppLongClick = { app ->
                showAppNameEditDialog(app)
                true
            }
        )
    }

    /**
     * 应用 adapter 到指定 RecyclerView（同步设置 layoutManager、decoration、padding）
     */
    private fun applyAdapterToRecycler(collection: Collection, adapter: HomeAppGridAdapter, recycler: RecyclerView) {
        recycler.layoutManager = GridLayoutManager(this, collection.gridColumns)
        recycler.adapter = adapter

        val rowSpacing = DialogSettings.getRowSpacing(this)
        val colSpacing = DialogSettings.getColSpacing(this)

        // 清除旧 decoration
        while (recycler.itemDecorationCount > 0) {
            recycler.removeItemDecorationAt(0)
        }
        recycler.addItemDecoration(SpacingItemDecoration(rowSpacing, colSpacing))

        val halfColPx = (colSpacing * resources.displayMetrics.density / 2).toInt()
        recycler.setPadding(halfColPx, 0, halfColPx, 0)
    }

    /**
     * 后台预加载所有标签页的数据到缓存
     */
    private fun preloadAllData() {
        allDialogCollections.forEachIndexed { i, col ->
            if (adapterCache.containsKey(i)) return@forEachIndexed
            val adapter = createAdapter(col)
            adapterCache[i] = adapter
            lifecycleScope.launch {
                val apps = withContext(Dispatchers.IO) {
                    viewModel.repository.getApps(col.id)
                }
                adapter.submitList(apps)
            }
        }
    }

    /**
     * 应用弹窗标题显示/隐藏
     */
    private fun applyTitleVisibility(_collection: Collection) {
        binding.titleContainer.visibility = View.GONE
    }

    private fun applyDialogLayout(_collection: Collection) {
        val density = resources.displayMetrics.density
        val marginPx = (DialogSettings.getMarginHorizontal(this) * density).toInt()

        // 全屏半透明窗口：卡片宽度 = 屏宽 - 2*边距（由 margin 决定），
        // 高度 wrap_content 随 APP 数自适应；位置由 layout_gravity 决定。全程不碰 window。
        val params = binding.contentCard.layoutParams as FrameLayout.LayoutParams
        params.marginStart = marginPx
        params.marginEnd = marginPx

        val position = DialogSettings.getPosition(this)
        if (position == "bottom") {
            params.gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            // 贴底时额外留出导航栏高度，避免被系统导航条遮挡
            params.bottomMargin = (DialogSettings.getBottomMargin(this) * density).toInt() + getNavigationBarHeight()
        } else {
            params.gravity = Gravity.CENTER
            params.bottomMargin = 0
        }
        binding.contentCard.layoutParams = params
    }

    /** 读取系统导航栏高度（无导航栏时返回 0） */
    private fun getNavigationBarHeight(): Int {
        val id = resources.getIdentifier("navigation_bar_height", "dimen", "android")
        return if (id > 0) resources.getDimensionPixelSize(id) else 0
    }

    /** 左右滑动手势检测器 */
    private var tabGestureDetector: GestureDetector? = null

    private fun setupDismissListeners() {
        val gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onFling(
                e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float
            ): Boolean {
                if (allDialogCollections.size <= 1) return false
                val dx = e2.x - (e1?.x ?: e2.x)
                val dy = e2.y - (e1?.y ?: e2.y)
                if (Math.abs(dx) > Math.abs(dy) && Math.abs(dx) > 40 && Math.abs(velocityX) > 300) {
                    if (dx < 0 && currentTabIndex < allDialogCollections.size - 1) {
                        switchToTab(currentTabIndex + 1)
                        return true
                    } else if (dx > 0 && currentTabIndex > 0) {
                        switchToTab(currentTabIndex - 1)
                        return true
                    }
                }
                return false
            }
        })
        tabGestureDetector = gestureDetector

        binding.root.setOnTouchListener { _, event ->
            // 点击弹窗外区域关闭（左右滑手势统一在 dispatchTouchEvent 中处理，
            // 以便手指落在图标网格上也能识别）
            if (event.action == MotionEvent.ACTION_DOWN) {
                val x = event.rawX.toInt()
                val y = event.rawY.toInt()

                val cardLoc = IntArray(2)
                binding.contentCard.getLocationOnScreen(cardLoc)
                val cardRight = cardLoc[0] + binding.contentCard.width
                val cardBottom = cardLoc[1] + binding.contentCard.height

                if (x < cardLoc[0] || x > cardRight || y < cardLoc[1] || y > cardBottom) {
                    dismissWithAnimation()
                    return@setOnTouchListener true
                }
            }
            false
        }
    }

    /**
     * 全局分发触摸事件给手势检测器：
     * 即使手指落在图标网格（RecyclerView）等子 View 上，也能识别左右滑切换标签页。
     * 不消费事件，正常点击/长按仍照常工作。
     */
    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        // 左右滑切换标签页的手势识别（手指落在卡片内也能识别）
        tabGestureDetector?.onTouchEvent(ev)
        return super.dispatchTouchEvent(ev)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        dismissWithAnimation()
    }

    override fun onDestroy() {
        super.onDestroy()
        autoDismiss.onActivityDestroyed()
        autoDismiss.uninstall()
    }

    private fun dismissWithAnimation() {
        binding.root.setOnTouchListener(null)
        val duration = (DialogSettings.getAnimDuration(this) * 0.6f).toLong()

        binding.contentCard.animate()
            .alpha(0f)
            .scaleX(0.92f)
            .scaleY(0.92f)
            .translationY(40f * resources.displayMetrics.density)
            .setDuration(duration)
            .setInterpolator(AccelerateInterpolator(2f))
            .withEndAction {
                finish()
            }
            .start()
    }

    private fun observeCollection() {
        viewModel.repository.observeCollection(collectionId).observe(this) { collection ->
            collection ?: return@observe
            currentTabCollection = collection
            if (allDialogCollections.isNotEmpty()) {
                setupTabsAndRecycler(collection)
            }
        }
    }

    private fun loadDialogCollections() {
        lifecycleScope.launch {
            val all = viewModel.repository.getAllCollections()
            allDialogCollections = all.filter { it.showInDialog }
            currentTabCollection?.let { setupTabsAndRecycler(it) }
        }
    }

    /**
     * 设置 TabLayout + RecyclerView（预缓存数据避免切换闪烁）
     */
    private fun setupTabsAndRecycler(initialCollection: Collection) {
        if (tabsInitialized || allDialogCollections.isEmpty()) return
        tabsInitialized = true

        val tabs = binding.collectionTabs

        // 找到初始 collection 的索引
        allDialogCollections.forEachIndexed { i, col ->
            if (col.id == initialCollection.id) currentTabIndex = i
        }

        // 应用全局标签页高度
        val tabHeightDp = DialogSettings.getTabHeightDp(this)
        val tabHeightPx = (tabHeightDp * resources.displayMetrics.density).toInt()
        tabs.layoutParams = tabs.layoutParams.apply { height = tabHeightPx }

        // 多标签时显示 tabs，否则隐藏
        tabs.visibility = if (allDialogCollections.size > 1) View.VISIBLE else View.GONE

        // 添加标签
        allDialogCollections.forEach { col -> tabs.addTab(tabs.newTab().setText(col.name)) }

        // 标签选择监听
        tabs.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                if (isProgrammaticTabSelect) return
                val pos = tab.position
                if (pos != currentTabIndex) {
                    switchToTab(pos)
                }
            }

            override fun onTabUnselected(tab: TabLayout.Tab) {}
            override fun onTabReselected(tab: TabLayout.Tab) {}
        })

        // 应用初始 collection 的样式
        applyDialogLayout(initialCollection)
        applyTitleVisibility(initialCollection)
        applyCardBackground()
        binding.collectionName.text = initialCollection.name

        // 创建初始 adapter 并加载数据
        val initialAdapter = createAdapter(initialCollection)
        adapterCache[currentTabIndex] = initialAdapter
        activeRecycler = binding.appsRecycler
        applyAdapterToRecycler(initialCollection, initialAdapter, binding.appsRecycler)
        lifecycleScope.launch {
            val apps = withContext(Dispatchers.IO) {
                viewModel.repository.getApps(initialCollection.id)
            }
            initialAdapter.submitList(apps)
        }

        // 后台预加载其余标签页
        preloadAllData()

        isProgrammaticTabSelect = true
        tabs.selectTab(tabs.getTabAt(currentTabIndex))
        isProgrammaticTabSelect = false

        playEnterAnimation(initialCollection)
    }

    /**
     * 获取指定标签页的 adapter（命中缓存则直接用，否则创建并异步加载数据）
     */
    private fun getAdapterFor(position: Int, col: Collection): HomeAppGridAdapter {
        return adapterCache[position] ?: run {
            val a = createAdapter(col)
            adapterCache[position] = a
            lifecycleScope.launch {
                val apps = withContext(Dispatchers.IO) {
                    viewModel.repository.getApps(col.id)
                }
                a.submitList(apps)
            }
            a
        }
    }

    /**
     * 切换到指定索引的标签页。
     * 采用双层 RecyclerView 交叉滑动：旧内容朝手势方向滑出、新内容从对侧滑入，
     * 两段动画并行（cross-fade），方向跟随手势，迅速且连贯。
     */
    private fun switchToTab(position: Int) {
        if (position < 0 || position >= allDialogCollections.size) return
        if (position == currentTabIndex) return
        if (isSwitching) return
        isSwitching = true

        // dir：+1 表示「去下一页」（左滑触发，旧内容向左、新内容从右）；-1 反之
        val dir = if (position > currentTabIndex) 1 else -1
        val density = resources.displayMetrics.density
        val offset = 64f * density
        val duration = 220L

        val col = allDialogCollections[position]
        val outgoing = activeRecycler
        val incoming = if (activeRecycler === binding.appsRecycler) binding.appsRecyclerB else binding.appsRecycler

        // 准备 incoming 层
        val adapter = getAdapterFor(position, col)
        applyAdapterToRecycler(col, adapter, incoming)
        incoming.alpha = 0f
        incoming.translationX = dir * offset
        incoming.visibility = View.VISIBLE

        // 锁定容器当前高度，避免切换瞬间卡片高度跳变；
        // 再手动测量 incoming 的自然高度（AT_MOST 封顶到屏幕 70%），用于平滑「长高/变矮」动画
        val container = binding.swapContainer
        val lockH = container.height
        val maxH = (resources.displayMetrics.heightPixels * 0.7).toInt()
        val targetH = if (lockH > 0) {
            val wSpec = View.MeasureSpec.makeMeasureSpec(container.width, View.MeasureSpec.EXACTLY)
            val hSpec = View.MeasureSpec.makeMeasureSpec(maxH, View.MeasureSpec.AT_MOST)
            incoming.measure(wSpec, hSpec)
            val m = incoming.measuredHeight
            if (m in 1..maxH) m else lockH
        } else {
            ViewGroup.LayoutParams.WRAP_CONTENT
        }

        if (lockH > 0) {
            container.layoutParams.height = lockH
            container.requestLayout()
        }

        // 旧层朝手势方向滑出淡出
        outgoing.animate()
            .alpha(0f)
            .translationX(-dir * offset)
            .setDuration(duration)
            .setInterpolator(AccelerateDecelerateInterpolator())
            // 滑出结束后彻底移出布局(GONE 不占位)，否则 INVISIBLE 仍占空间，
            // 导致容器 WRAP_CONTENT 时把旧(高)层一起量进去，高→矮切换后「弹回高」
            .withEndAction { outgoing.visibility = View.GONE }
            .start()

        // 新层从对侧滑入淡入（与旧层并行，形成交叉过渡）
        incoming.animate()
            .alpha(1f)
            .translationX(0f)
            .setDuration(duration)
            .setInterpolator(AccelerateDecelerateInterpolator())
            .start()

        // 容器高度从旧高度平滑补间到新内容高度（与滑入同步），让弹窗优雅地「长高/变矮」
        if (lockH > 0 && targetH > 0) {
            ValueAnimator.ofInt(lockH, targetH).apply {
                setDuration(duration + 40L)
                interpolator = AccelerateDecelerateInterpolator()
                addUpdateListener { anim ->
                    container.layoutParams.height = anim.animatedValue as Int
                    container.requestLayout()
                }
                doOnEnd {
                    container.layoutParams.height = ViewGroup.LayoutParams.WRAP_CONTENT
                    container.requestLayout()
                }
                start()
            }
        }

        // 同步切换状态
        currentTabIndex = position
        currentTabCollection = col
        isProgrammaticTabSelect = true
        binding.collectionTabs.selectTab(binding.collectionTabs.getTabAt(position))
        isProgrammaticTabSelect = false
        activeRecycler = incoming

        // 动画结束后释放切换锁，略微延迟确保稳定
        outgoing.postDelayed({ isSwitching = false }, duration + 80)
    }

    /**
     * 应用弹窗背景。
     * - Android 12+：Window.setBackgroundBlurRadius，由系统合成器对「窗口背后真实内容」做模糊，
     *   iOS 同款毛玻璃效果，且零额外内存（不在 App 内分配位图，GPU 合成）。
     * - Android 11：原生模糊不可用，回退为「截取壁纸 -> 缩放位图 -> 模糊」（占用一张缩放位图，内存很小）。
     */
    private fun applyCardBackground() {
        val bgColor = DialogSettings.getBgColor(this)
        val bgAlpha = DialogSettings.getBgAlpha(this)
        val blurRadius = DialogSettings.getBlurRadius(this)
        val hasColor = bgColor != 0

        binding.contentCard.elevation = 0f

        val baseColor = if (hasColor) bgColor
            else ContextCompat.getColor(this, R.color.surface)

        if (blurRadius > 0 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // ===== Android 12+ 原生毛玻璃（整窗模糊）=====
            val px = (blurRadius * 4f).toInt().coerceAtLeast(1)
            window?.setBackgroundBlurRadius(px)

            // [FIX 2.10] 整窗模糊模式下，卡片内 overlay 必须保持 GONE！
            // 它们是 match_parent，若设为 VISIBLE 会把 MaterialCardView(FrameLayout) 的 wrap_content 撑到整屏高。
            // 整窗已模糊，直接给卡片设半透明背景色即可达到同样的视觉效果。
            binding.blurBackground.visibility = View.GONE
            binding.colorOverlay.visibility = View.GONE
            binding.noiseOverlay.visibility = View.GONE

            // 卡片半透明背景色，让整窗毛玻璃效果自然透出
            val cardAlpha = bgAlpha.coerceIn(30, 200)
            binding.contentCard.setCardBackgroundColor(
                BackgroundHelper.applyAlphaToColor(baseColor, cardAlpha)
            )
            binding.root.setBackgroundColor(Color.TRANSPARENT)
        } else {
            // ===== 关闭模糊：纯色背景，并关掉原生模糊 =====
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                window?.setBackgroundBlurRadius(0)
            }
            binding.blurBackground.visibility = View.GONE
            binding.colorOverlay.visibility = View.GONE
            binding.noiseOverlay.visibility = View.GONE
            binding.contentCard.setCardBackgroundColor(
                BackgroundHelper.applyAlphaToColor(baseColor, bgAlpha)
            )
            // 卡片外保持透明：背后内容正常显示（不磨砂、不暗化），仅卡片本身有颜色
            binding.root.setBackgroundColor(Color.TRANSPARENT)
        }
    }


    private fun getWallpaperBitmap(): Bitmap? {
        return try {
            val wallpaperManager = WallpaperManager.getInstance(this)
            val drawable = wallpaperManager.drawable ?: return null

            if (drawable is BitmapDrawable && drawable.bitmap != null) {
                drawable.bitmap
            } else {
                val w = drawable.intrinsicWidth.coerceAtLeast(1)
                val h = drawable.intrinsicHeight.coerceAtLeast(1)
                val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bitmap)
                drawable.setBounds(0, 0, w, h)
                drawable.draw(canvas)
                bitmap
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun scaleBitmapToMaxSize(bitmap: Bitmap, maxSize: Int): Bitmap {
        val maxDim = maxOf(bitmap.width, bitmap.height)
        if (maxDim <= maxSize) return bitmap
        val scale = maxSize.toFloat() / maxDim
        val w = (bitmap.width * scale).toInt()
        val h = (bitmap.height * scale).toInt()
        return Bitmap.createScaledBitmap(bitmap, w, h, true)
    }

    private fun isDarkColor(color: Int): Boolean {
        val r = Color.red(color)
        val g = Color.green(color)
        val b = Color.blue(color)
        val luminance = (0.299 * r + 0.587 * g + 0.114 * b) / 255.0
        return luminance < 0.5
    }

    private fun getBackgroundMode(): String {
        val bgColor = DialogSettings.getBgColor(this)
        if (bgColor != 0) {
            return if (isDarkColor(bgColor)) "dark" else "light"
        }
        return "default"
    }

    private class SpacingItemDecoration(
        private val rowSpacingDp: Int,
        private val columnSpacingDp: Int
    ) : RecyclerView.ItemDecoration() {

        override fun getItemOffsets(
            outRect: Rect,
            view: View,
            parent: RecyclerView,
            state: RecyclerView.State
        ) {
            val density = parent.context.resources.displayMetrics.density
            val rowPx = (rowSpacingDp * density).toInt()
            val colPx = (columnSpacingDp * density).toInt()

            outRect.left = colPx / 2
            outRect.right = colPx / 2
            outRect.top = rowPx / 2
            outRect.bottom = rowPx / 2
        }
    }

    companion object {
        const val EXTRA_COLLECTION_ID = "collection_id"
    }
}
