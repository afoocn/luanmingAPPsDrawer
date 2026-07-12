package com.fengnian.folderdrawer.adapter

import android.content.Context
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import androidx.core.content.ContextCompat
import com.fengnian.folderdrawer.R
import com.fengnian.folderdrawer.data.AppItem
import com.fengnian.folderdrawer.iconpack.IconPackManager
import com.fengnian.folderdrawer.util.AppUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 弹窗里的 App 网格 Adapter
 * 支持动态配置：图标大小、名称显隐、紧凑模式、名称字? * 支持文字颜色：自定义颜色优先，否则根据背景模式自动适配
 * 始终带阴影保证可读? *
 * @param backgroundMode "dark"=深色背景(白字), "light"=浅色背景(黑字), "default"=默认(深色?
 * @param appNameTextColor 自定义APP名称颜色? = 自动
 */
class HomeAppGridAdapter(
    private val context: Context,
    private val iconPackManager: IconPackManager,
    private val scope: CoroutineScope,
    private val iconSizeDp: Int = 48,
    private val showAppName: Boolean = true,
    private val compactAppName: Boolean = false,
    private val appNameSizeSp: Int = 11,
    private val appNameTextColor: Int = 0,
    private val backgroundMode: String = "default",
    private val onAppClick: (AppItem) -> Unit,
    private val onAppLongClick: (AppItem) -> Boolean
) : ListAdapter<AppItem, HomeAppGridAdapter.ViewHolder>(DIFF) {

    private val density = context.resources.displayMetrics.density

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val icon: ImageView = itemView.findViewById(R.id.appIcon)
        val name: TextView = itemView.findViewById(R.id.appName)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(context)
            .inflate(R.layout.item_home_app, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val app = getItem(position)

        // 动态设置图标大小
        val iconSizePx = (iconSizeDp * density).toInt()
        holder.icon.layoutParams = LinearLayout.LayoutParams(iconSizePx, iconSizePx)

        // 动态设置名称
        if (showAppName) {
            holder.name.visibility = View.VISIBLE
            holder.name.text = app.displayName
            holder.name.textSize = appNameSizeSp.toFloat()
            if (compactAppName) {
                holder.name.setSingleLine(true)
                holder.name.ellipsize = android.text.TextUtils.TruncateAt.END
            } else {
                holder.name.setSingleLine(false)
                holder.name.maxLines = 2
                holder.name.minLines = 1
                holder.name.ellipsize = android.text.TextUtils.TruncateAt.END
            }

            applyAppNameStyle(holder.name)
        } else {
            holder.name.visibility = View.GONE
        }

        holder.itemView.setOnClickListener { onAppClick(app) }
        // 长按事件：先返回 onAppLongClick 决定是否消费
        // 如果回调返回 false（默认），系统会继续处理
        holder.itemView.setOnLongClickListener {
            val consumed = onAppLongClick(app)
            consumed
        }

        // 异步加载图标
        scope.launch {
            val drawable = withContext(Dispatchers.IO) {
                iconPackManager.getIcon(
                    app.packageName,
                    app.activityClassName,
                    app.displayName
                ) ?: AppUtils.getAppIcon(context, app.packageName, app.activityClassName)
            }
            holder.icon.setImageDrawable(drawable)
        }
    }

    /**
     * 设置 APP 名称的文字颜色和阴影
     * - 用户自定义颜色优?     * - 否则根据背景模式自动选择
     * - 始终加阴影保证可读?     */
    private fun applyAppNameStyle(nameView: TextView) {
        val textColor: Int
        val shadowColor: Int
        val shadowRadius: Float

        if (appNameTextColor != 0) {
            // 用户自定义颜色
            textColor = appNameTextColor
            // 根据文字颜色亮度选择阴影颜色
            val r = Color.red(textColor)
            val g = Color.green(textColor)
            val b = Color.blue(textColor)
            val luminance = (0.299 * r + 0.587 * g + 0.114 * b) / 255.0
            shadowColor = if (luminance > 0.5) Color.argb(160, 0, 0, 0) else Color.argb(120, 255, 255, 255)
            shadowRadius = 5f
        } else {
            // 自动模式
            when (backgroundMode) {
                "dark" -> {
                    textColor = Color.WHITE
                    shadowColor = Color.argb(160, 0, 0, 0)
                    shadowRadius = 5f
                }
                "light" -> {
                    textColor = Color.argb(230, 0, 0, 0)
                    shadowColor = Color.argb(120, 255, 255, 255)
                    shadowRadius = 4f
                }
                else -> {
                    textColor = ContextCompat.getColor(context, R.color.on_surface)
                    shadowColor = Color.argb(140, 0, 0, 0)
                    shadowRadius = 4f
                }
            }
        }

        nameView.setTextColor(textColor)
        nameView.setShadowLayer(shadowRadius, 0f, 1f, shadowColor)
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
