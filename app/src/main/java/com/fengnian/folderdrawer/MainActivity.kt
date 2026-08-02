package com.fengnian.folderdrawer

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.fengnian.folderdrawer.adapter.CollectionCardAdapter
import com.fengnian.folderdrawer.AppDialerActivity
import com.fengnian.folderdrawer.DialerConstants
import com.fengnian.folderdrawer.DialerSettingsActivity
import com.fengnian.folderdrawer.data.Collection
import com.fengnian.folderdrawer.databinding.ActivityMainBinding
import com.fengnian.folderdrawer.iconpack.IconPackManager
import com.fengnian.folderdrawer.util.DialogSettings
import com.fengnian.folderdrawer.util.ShortcutHelper
import com.fengnian.folderdrawer.viewmodel.CollectionViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: CollectionViewModel by viewModels()
    private lateinit var iconPackManager: IconPackManager
    private lateinit var adapter: CollectionCardAdapter

    private val editCollectionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { _ -> adapter.notifyDataSetChanged() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.title = getString(R.string.collections_title)

        iconPackManager = IconPackManager.getInstance(this)
        iconPackManager.refreshAvailablePacks()

        setupRecycler()
        observeCollections()
        setupAddButton()
        setupSettingsButton()
    }

    private fun setupRecycler() {
        adapter = CollectionCardAdapter(
            context = this,
            iconPackManager = iconPackManager,
            scope = lifecycleScope,
            onCollectionClick = { collection -> openCollectionDrawer(collection) },
            onPinClick = { collection -> pinToHomeScreen(collection) },
            onEditClick = { collection ->
                if (collection.id == DialerConstants.DIALER_COLLECTION_ID) {
                    // 拨号盘卡片的铅笔按钮：进入专属设置页
                    startActivity(Intent(this, DialerSettingsActivity::class.java))
                } else {
                    editCollection(collection)
                }
            },
            onDeleteClick = { collection ->
                if (collection.id == DialerConstants.DIALER_COLLECTION_ID) {
                    // 拨号盘是虚拟卡片（不入数据库），其「删除」= 关闭功能总开关并移除卡片
                    confirmDisableDialer()
                } else {
                    confirmDeleteCollection(collection)
                }
            },
            onShowInDialogChanged = { collection, checked ->
                lifecycleScope.launch {
                    viewModel.repository.updateCollection(collection.copy(showInDialog = checked))
                }
            },
            onMove = { _, _ ->
                lifecycleScope.launch {
                    // 排除特殊项（id=-2），仅对真实抽屉更新排序
                    val realList = adapter.currentList.filter { it.id != DialerConstants.DIALER_COLLECTION_ID }
                    viewModel.repository.updateSortOrders(realList)
                }
            }
        )
        binding.collectionsRecycler.layoutManager = LinearLayoutManager(this)
        binding.collectionsRecycler.adapter = adapter

        // 长按拖拽排序
        val itemTouchHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0
        ) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                val from = viewHolder.bindingAdapterPosition
                val to = target.bindingAdapterPosition
                adapter.onItemMove(from, to)
                return true
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {}

            override fun isLongPressDragEnabled() = true
        })
        itemTouchHelper.attachToRecyclerView(binding.collectionsRecycler)
    }

    private fun observeCollections() {
        viewModel.collections.observe(this) { list ->
            submitWithDialer(list)
        }
    }

    /**
     * 组装主列表并提交：仅当「启用 APP Dialer」开关打开时才追加虚拟拨号盘卡片，
     * 关闭则隐藏入口并停止发布其动态快捷方式。
     */
    private fun submitWithDialer(list: List<Collection>) {
        val full = ArrayList(list)
        if (DialogSettings.isDialerEnabled(this)) {
            full.add(virtualDialerCollection())
        }
        binding.emptyView.visibility = if (list.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE
        binding.collectionsRecycler.visibility = android.view.View.VISIBLE
        adapter.submitList(full)
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                ShortcutHelper.publishDynamicShortcuts(this@MainActivity, full)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // 开关可能在「全局设置」里被切换，返回时按当前开关重算拨号盘卡片可见性
        viewModel.collections.value?.let { submitWithDialer(it) }
    }

    /** 构造「APP Dialer」虚拟抽屉对象（id=-2，仅用于主列表展示与快捷方式，不入数据库） */
    private fun virtualDialerCollection(): Collection {
        return Collection(
            id = DialerConstants.DIALER_COLLECTION_ID,
            name = "APP Dialer",
            iconColor = ContextCompat.getColor(this, R.color.primary)
        )
    }

    private fun setupAddButton() {
        binding.addCollectionButton.setOnClickListener {
            editCollectionLauncher.launch(
                Intent(this, CollectionEditActivity::class.java).apply {
                    putExtra(CollectionEditActivity.EXTRA_IS_NEW, true)
                }
            )
        }
    }

    private fun setupSettingsButton() {
        binding.settingsButton.setOnClickListener {
            startActivity(Intent(this, GlobalDialogSettingsActivity::class.java))
        }
    }

    private fun pinToHomeScreen(collection: Collection) {
        val ok = ShortcutHelper.pinCollectionToHome(this, collection)
        if (ok) {
            Toast.makeText(this, getString(R.string.shortcut_pinning), Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, getString(R.string.shortcut_pin_failed), Toast.LENGTH_LONG).show()
        }
    }

    private fun openCollectionDrawer(collection: Collection) {
        if (collection.id == DialerConstants.DIALER_COLLECTION_ID) {
            // 特殊抽屉：直接打开拨号盘弹窗（开关关闭时不应出现卡片，这里再做一次保护）
            if (!DialogSettings.isDialerEnabled(this)) return
            startActivity(Intent(this, AppDialerActivity::class.java))
            return
        }
        val intent = Intent(this, QuickLaunchDialogActivity::class.java).apply {
            putExtra(QuickLaunchDialogActivity.EXTRA_COLLECTION_ID, collection.id)
        }
        startActivity(intent)
    }

    private fun editCollection(collection: Collection) {
        editCollectionLauncher.launch(
            Intent(this, CollectionEditActivity::class.java).apply {
                putExtra(CollectionEditActivity.EXTRA_IS_NEW, false)
                putExtra(CollectionEditActivity.EXTRA_COLLECTION_ID, collection.id)
            }
        )
    }

    private fun confirmDeleteCollection(collection: Collection) {
        val dialog = android.app.Dialog(this)
        val view = layoutInflater.inflate(R.layout.dialog_confirm_delete, null)

        view.findViewById<android.widget.TextView>(R.id.deleteMessage).text =
            getString(R.string.confirm_delete_message, collection.name)

        view.findViewById<android.widget.TextView>(R.id.btnCancelDelete).setOnClickListener {
            dialog.dismiss()
        }
        view.findViewById<android.widget.TextView>(R.id.btnConfirmDelete).setOnClickListener {
            dialog.dismiss()
            lifecycleScope.launch {
                viewModel.repository.deleteCollection(collection.id)
            }
        }

        dialog.setContentView(view)
        dialog.window?.apply {
            setBackgroundDrawable(
                android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT)
            )
            setLayout(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        dialog.show()
    }

    /**
     * 关闭 APP Dialer 功能确认框（对应主列表虚拟卡片的「删除」按钮）。
     * 与普通抽屉的「删除」不同：拨号盘是虚拟卡片、不入数据库，故此处仅关闭全局总开关，
     * 列表重算后即移除虚拟卡片，并停止发布其动态快捷方式（长按菜单不再出现）。
     */
    private fun confirmDisableDialer() {
        val dialog = android.app.Dialog(this)
        val view = layoutInflater.inflate(R.layout.dialog_confirm_delete, null)

        view.findViewById<android.widget.TextView>(R.id.deleteMessage).text =
            getString(R.string.confirm_disable_dialer_message)

        view.findViewById<android.widget.TextView>(R.id.btnCancelDelete).setOnClickListener {
            dialog.dismiss()
        }
        view.findViewById<android.widget.TextView>(R.id.btnConfirmDelete).setOnClickListener {
            dialog.dismiss()
            DialogSettings.setDialerEnabled(this, false)
            // 开关已同步写入内存（apply 立即可见），重算列表即移除虚拟卡片与动态快捷方式
            viewModel.collections.value?.let { submitWithDialer(it) }
        }

        dialog.setContentView(view)
        dialog.window?.apply {
            setBackgroundDrawable(
                android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT)
            )
            setLayout(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        dialog.show()
    }
}
