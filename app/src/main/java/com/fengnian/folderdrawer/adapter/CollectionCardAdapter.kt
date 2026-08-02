package com.fengnian.folderdrawer.adapter

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.fengnian.folderdrawer.R
import com.fengnian.folderdrawer.DialerConstants
import com.fengnian.folderdrawer.data.Collection
import com.fengnian.folderdrawer.iconpack.IconPackManager
import com.fengnian.folderdrawer.util.AppUtils
import com.google.android.material.materialswitch.MaterialSwitch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class CollectionCardAdapter(
    private val context: Context,
    private val iconPackManager: IconPackManager,
    private val scope: CoroutineScope,
    private val onCollectionClick: (Collection) -> Unit,
    private val onPinClick: (Collection) -> Unit,
    private val onEditClick: (Collection) -> Unit,
    private val onDeleteClick: (Collection) -> Unit,
    private val onShowInDialogChanged: (Collection, Boolean) -> Unit = { _, _ -> },
    private val onMove: ((Int, Int) -> Unit)? = null
) : ListAdapter<Collection, CollectionCardAdapter.ViewHolder>(DIFF) {

    private val iconSizePx = (36 * context.resources.displayMetrics.density).toInt()

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val nameText: TextView = itemView.findViewById(R.id.collectionName)
        val pinButton: ImageView = itemView.findViewById(R.id.pinButton)
        val editButton: ImageView = itemView.findViewById(R.id.editButton)
        val deleteButton: ImageView = itemView.findViewById(R.id.deleteButton)
        val appsPreview: LinearLayout = itemView.findViewById(R.id.appsPreview)
        val extraCount: TextView = itemView.findViewById(R.id.extraCount)
        val showInDialogSwitch: MaterialSwitch = itemView.findViewById(R.id.showInDialogSwitch)
        val showInDialogRow: View = itemView.findViewById(R.id.showInDialogRow)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(context)
            .inflate(R.layout.item_collection_card, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val collection = getItem(position)
        val isDialer = collection.id == DialerConstants.DIALER_COLLECTION_ID
        holder.nameText.text = collection.name

        holder.itemView.setOnClickListener { onCollectionClick(collection) }
        holder.pinButton.setOnClickListener { onPinClick(collection) }
        holder.editButton.setOnClickListener { onEditClick(collection) }
        holder.deleteButton.setOnClickListener { onDeleteClick(collection) }

        if (isDialer) {
            // 特殊项：不能删除/排序、不可在弹窗内显示、无应用预览；
            // 仅显示「钉桌面」与「铅笔（设置）」按钮。点击铅笔进入拨号盘专属设置页。
            holder.editButton.visibility = View.VISIBLE
            // 删除按钮：点击后弹「是否关闭 APP Dialer 功能」确认框（见 MainActivity.confirmDisableDialer）
            holder.deleteButton.visibility = View.VISIBLE
            holder.showInDialogRow.visibility = View.GONE
            holder.appsPreview.visibility = View.GONE
            holder.extraCount.visibility = View.GONE
            return
        }

        loadAppPreviews(holder, collection)

        holder.showInDialogSwitch.isChecked = collection.showInDialog
        holder.showInDialogSwitch.setOnCheckedChangeListener { _, isChecked ->
            onShowInDialogChanged(collection, isChecked)
        }
    }

    private fun loadAppPreviews(holder: ViewHolder, collection: Collection) {
        scope.launch {
            val apps = withContext(Dispatchers.IO) {
                AppUtils.getCollectionApps(context, collection.id)
            }
            val previewCount = 5
            val displayApps = apps.take(previewCount)
            val totalCount = apps.size

            holder.appsPreview.removeAllViews()
            displayApps.forEach { app ->
                val iv = ImageView(context).apply {
                    layoutParams = LinearLayout.LayoutParams(iconSizePx, iconSizePx).apply {
                        marginEnd = (8 * context.resources.displayMetrics.density).toInt()
                    }
                    clipToOutline = true
                }
                holder.appsPreview.addView(iv)

                scope.launch {
                    val icon = withContext(Dispatchers.IO) {
                        iconPackManager.getIcon(
                            app.packageName,
                            app.activityName,
                            app.displayName
                        )
                    }
                    iv.setImageDrawable(icon)
                }
            }

            if (totalCount > 0) {
                holder.extraCount.visibility = View.VISIBLE
                holder.extraCount.text = totalCount.toString()
            } else {
                holder.extraCount.visibility = View.VISIBLE
                holder.extraCount.text = "0"
            }
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<Collection>() {
            override fun areItemsTheSame(oldItem: Collection, newItem: Collection) =
                oldItem.id == newItem.id

            override fun areContentsTheSame(oldItem: Collection, newItem: Collection) =
                oldItem == newItem
        }
    }

    fun onItemMove(fromPosition: Int, toPosition: Int) {
        val list = currentList.toMutableList()
        val moved = list[fromPosition]
        if (moved.id == DialerConstants.DIALER_COLLECTION_ID) return  // 特殊项不可移动
        list.removeAt(fromPosition)
        list.add(toPosition, moved)
        submitList(list)
        onMove?.invoke(fromPosition, toPosition)
    }
}
