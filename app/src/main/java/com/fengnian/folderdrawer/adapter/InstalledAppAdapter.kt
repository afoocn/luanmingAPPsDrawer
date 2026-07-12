package com.fengnian.folderdrawer.adapter

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.fengnian.folderdrawer.R
import com.fengnian.folderdrawer.databinding.ItemAppPickerBinding
import com.fengnian.folderdrawer.iconpack.IconPackManager
import com.fengnian.folderdrawer.util.InstalledApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class InstalledAppAdapter(
    private val context: Context,
    private val iconPackManager: IconPackManager,
    private val scope: CoroutineScope,
    private val alreadyAdded: MutableSet<String>,
    private val onToggle: (InstalledApp, Boolean) -> Unit
) : ListAdapter<InstalledApp, InstalledAppAdapter.VH>(DIFF) {

    inner class VH(val binding: ItemAppPickerBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        return VH(
            ItemAppPickerBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
        )
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val app = getItem(position)
        val key = "${app.packageName}/${app.activityName}"
        val isAdded = alreadyAdded.contains(key)

        with(holder.binding) {
            tvAppName.text = app.label
            tvAppPackage.text = app.packageName

            ivAppIcon.setImageDrawable(app.icon)

            // 选中态：整行高亮 + 圆形勾选
            root.setBackgroundResource(
                if (isAdded) R.drawable.bg_app_picker_item_selected
                else R.drawable.bg_app_picker_item
            )
            ivCheck.setImageResource(
                if (isAdded) R.drawable.ic_check_selected
                else R.drawable.ic_check_unselected
            )

            scope.launch {
                val drawable = withContext(Dispatchers.IO) {
                    iconPackManager.getIcon(app.packageName, app.activityName, app.label)
                }
                if (holder.bindingAdapterPosition == position) {
                    ivAppIcon.setImageDrawable(drawable)
                }
            }

            root.alpha = 1f
            root.isClickable = true
            root.setOnClickListener {
                // 关键：以 alreadyAdded 的实时状态为准，而非绑定时捕获的 isAdded，
                // 否则"所有"视图不重绘时点击监听里的旧值会导致只能选不能反选。
                val newState = !alreadyAdded.contains(key)
                root.setBackgroundResource(
                    if (newState) R.drawable.bg_app_picker_item_selected
                    else R.drawable.bg_app_picker_item
                )
                ivCheck.setImageResource(
                    if (newState) R.drawable.ic_check_selected
                    else R.drawable.ic_check_unselected
                )
                onToggle(app, newState)
            }
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<InstalledApp>() {
            override fun areItemsTheSame(
                oldItem: InstalledApp,
                newItem: InstalledApp
            ) = oldItem.packageName == newItem.packageName &&
                    oldItem.activityName == newItem.activityName

            override fun areContentsTheSame(
                oldItem: InstalledApp,
                newItem: InstalledApp
            ) = oldItem.packageName == newItem.packageName &&
                    oldItem.activityName == newItem.activityName
        }
    }
}
