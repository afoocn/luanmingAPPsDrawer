package com.fengnian.folderdrawer

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.fengnian.folderdrawer.databinding.ActivityIconPickerBinding
import com.fengnian.folderdrawer.iconpack.IconPackManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 图标选择器：从指?Icon Pack（或当前激活的）中选择一个图? *
 * 可通过 EXTRA_ICON_PACK_PACKAGE 指定从哪个图标包加载? * 不传则使用当前激活的图标主题? */
class IconPickerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityIconPickerBinding
    private lateinit var iconPackManager: IconPackManager
    private var adapter: IconGridAdapter? = null
    private var useTempPack = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityIconPickerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }

        iconPackManager = IconPackManager.getInstance(this)

        // 如果传入了指定的 icon pack 包名，临时加载它
        val packPackage = intent.getStringExtra(EXTRA_ICON_PACK_PACKAGE)
        if (!packPackage.isNullOrBlank()) {
            useTempPack = iconPackManager.loadPack(packPackage)
            if (!useTempPack) {
                binding.tvEmpty.visibility = View.VISIBLE
                binding.tvEmpty.text = "无法加载该图标主题"
                return
            }
        }

        loadIcons()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (useTempPack) {
            iconPackManager.clearTempPack()
        }
    }

    private fun loadIcons() {
        binding.progressBar.visibility = View.VISIBLE
        binding.tvEmpty.visibility = View.GONE

        lifecycleScope.launch {
            val names = withContext(Dispatchers.IO) {
                if (useTempPack) iconPackManager.getAllDrawableNamesFromTemp()
                else iconPackManager.getAllDrawableNames()
            }

            binding.progressBar.visibility = View.GONE

            if (names.isEmpty()) {
                binding.tvEmpty.visibility = View.VISIBLE
                return@launch
            }

            adapter = IconGridAdapter(names, useTempPack) { drawableName ->
                val data = Intent().apply {
                    putExtra(EXTRA_ICON_DRAWABLE_NAME, drawableName)
                }
                setResult(Activity.RESULT_OK, data)
                finish()
            }
            binding.iconGrid.layoutManager = GridLayoutManager(this@IconPickerActivity, 5)
            binding.iconGrid.adapter = adapter
        }
    }

    companion object {
        const val EXTRA_ICON_DRAWABLE_NAME = "icon_drawable_name"
        const val EXTRA_ICON_PACK_PACKAGE = "icon_pack_package"
    }
}

/**
 * 图标网格 Adapter
 */
class IconGridAdapter(
    private val items: List<String>,
    private val useTempPack: Boolean,
    private val onClick: (String) -> Unit
) : RecyclerView.Adapter<IconGridAdapter.VH>() {

    inner class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val iconImage: ImageView = itemView.findViewById(R.id.iconImage)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_icon_picker, parent, false)
        return VH(view)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val drawableName = items[position]
        val iconPackManager = IconPackManager.getInstance(holder.itemView.context)

        holder.itemView.setOnClickListener { onClick(drawableName) }

        holder.iconPackScope()?.launch {
            val drawable = withContext(Dispatchers.IO) {
                if (useTempPack) iconPackManager.getDrawableByNameFromTemp(drawableName)
                else iconPackManager.getDrawableByName(drawableName)
            }
            holder.iconImage.setImageDrawable(drawable)
        }
    }

    override fun getItemCount() = items.size

    private fun VH.iconPackScope() = (itemView.context as? AppCompatActivity)?.lifecycleScope
}
