package com.fengnian.folderdrawer.adapter

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.fengnian.folderdrawer.R
import com.fengnian.folderdrawer.data.AppItem
import com.fengnian.folderdrawer.iconpack.IconPackManager
import com.fengnian.folderdrawer.util.AppUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 编辑界面里的 App 网格 Adapter?列）
 * 每个项有删除按钮，点击删除按钮回调移? * 长按 APP 卡片 ?回调 onAppLongClick（用于替换图标）
 */
class EditAppGridAdapter(
    private val context: Context,
    private val iconPackManager: IconPackManager,
    private val scope: CoroutineScope,
    private val onRemoveClick: (AppItem) -> Unit,
    private val onAppLongClick: (AppItem) -> Unit = {},
    private val onItemMove: ((Int, Int) -> Unit)? = null
) : ListAdapter<AppItem, EditAppGridAdapter.ViewHolder>(DIFF) {

    /** 拖拽排序进行中时置为 true，防止拖拽结束后图标 onClick 误弹替换菜单 */
    @Volatile
    var isDragging: Boolean = false

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val icon: ImageView = itemView.findViewById(R.id.appIcon)
        val name: TextView = itemView.findViewById(R.id.appName)
        val removeButton: View = itemView.findViewById(R.id.removeButton)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(context)
            .inflate(R.layout.item_edit_app, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val app = getItem(position)
        holder.name.text = app.displayName

        holder.removeButton.setOnClickListener { onRemoveClick(app) }

        // 单击图标 → 替换图标（拖拽中忽略）
        holder.icon.setOnClickListener {
            if (isDragging) return@setOnClickListener
            onAppLongClick(app)
        }

        // 长按卡片（图标外区域）→ ItemTouchHelper 拖拽排序
        // isLongPressDragEnabled=true 自动处理

        // 异步加载图标（优先用自定义图标，否则 Icon Pack，再否则系统图标）
        scope.launch {
            val drawable = withContext(Dispatchers.IO) {
                loadAppIcon(app)
            }
            holder.icon.setImageDrawable(drawable)
        }
    }

    fun moveItem(from: Int, to: Int) {
        val currentList = currentList.toMutableList()
        val moved = currentList.removeAt(from)
        currentList.add(to, moved)
        submitList(currentList)
        onItemMove?.invoke(from, to)
    }

    /**
     * 加载 APP 图标?     * 1. 如果设置?customIconPath（图片文件），直接读?     * 2. 否则 Icon Pack.getIcon()（自动适配?     * 3. 兜底系统 PackageManager 图标
     */
    private fun loadAppIcon(app: AppItem): Drawable? {
        if (!app.customIconPath.isNullOrBlank()) {
            val file = File(app.customIconPath)
            if (file.exists()) {
                try {
                    val bitmap = BitmapFactory.decodeFile(file.absolutePath)
                    if (bitmap != null) return android.graphics.drawable.BitmapDrawable(
                        context.resources, bitmap
                    )
                } catch (_: Exception) { }
            }
        }
        return iconPackManager.getIcon(
            app.packageName,
            app.activityClassName,
            app.displayName
        ) ?: AppUtils.getAppIcon(context, app.packageName, app.activityClassName)
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<AppItem>() {
            override fun areItemsTheSame(oldItem: AppItem, newItem: AppItem) =
                oldItem.id == newItem.id

            override fun areContentsTheSame(oldItem: AppItem, newItem: AppItem) =
                oldItem == newItem
        }
    }
}
