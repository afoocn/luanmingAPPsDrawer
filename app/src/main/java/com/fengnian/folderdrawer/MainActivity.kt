package com.fengnian.folderdrawer

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.fengnian.folderdrawer.adapter.CollectionCardAdapter
import com.fengnian.folderdrawer.data.Collection
import com.fengnian.folderdrawer.databinding.ActivityMainBinding
import com.fengnian.folderdrawer.iconpack.IconPackManager
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
            onEditClick = { collection -> editCollection(collection) },
            onDeleteClick = { collection -> confirmDeleteCollection(collection) },
            onShowInDialogChanged = { collection, checked ->
                lifecycleScope.launch {
                    viewModel.repository.updateCollection(collection.copy(showInDialog = checked))
                }
            },
            onMove = { fromPos, toPos ->
                lifecycleScope.launch {
                    val mutableList = adapter.currentList.toMutableList()
                    val moved = mutableList.removeAt(fromPos)
                    mutableList.add(toPos, moved)
                    viewModel.repository.updateSortOrders(mutableList)
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
            val isEmpty = list.isEmpty()
            // 空列表时显示引导视图，隐藏 RecyclerView
            binding.emptyView.visibility = if (isEmpty) android.view.View.VISIBLE else android.view.View.GONE
            binding.collectionsRecycler.visibility = if (isEmpty) android.view.View.GONE else android.view.View.VISIBLE
            adapter.submitList(ArrayList(list))
            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    ShortcutHelper.publishDynamicShortcuts(this@MainActivity, list)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
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
}
