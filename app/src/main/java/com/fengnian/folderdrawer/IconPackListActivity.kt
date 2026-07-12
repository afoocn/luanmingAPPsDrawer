package com.fengnian.folderdrawer

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.fengnian.folderdrawer.databinding.ActivityIconPackBinding
import com.fengnian.folderdrawer.iconpack.IconPackManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class IconPackListActivity : AppCompatActivity() {

    private lateinit var binding: ActivityIconPackBinding
    private lateinit var iconPackManager: IconPackManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityIconPackBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.title = "图标主题"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        iconPackManager = IconPackManager.getInstance(this)

        loadIconPacks()
    }

    private fun loadIconPacks() {
        binding.progressBar.visibility = View.VISIBLE
        binding.tvEmpty.visibility = View.GONE

        lifecycleScope.launch {
            val packs = withContext(Dispatchers.IO) {
                iconPackManager.getInstalledIconPacks()
            }
            val activePack = iconPackManager.getActiveIconPack()

            withContext(Dispatchers.Main) {
                binding.progressBar.visibility = View.GONE
                binding.radioGroup.removeAllViews()

                // "None" option (system icons)
                val noneRadio = RadioButton(this@IconPackListActivity).apply {
                    text = "系统默认图标"
                    tag = null
                    isChecked = activePack.isNullOrEmpty()
                    val padding = (16 * resources.displayMetrics.density).toInt()
                    setPadding(padding, padding, padding, padding)
                }
                binding.radioGroup.addView(noneRadio)

                if (packs.isEmpty()) {
                    binding.tvEmpty.visibility = View.VISIBLE
                    binding.tvEmpty.text = "未检测到图标主题应用\n请先安装 Icon Pack（如 Neo Icon Pack、Delta Icon Pack 等）"
                }

                for (pack in packs) {
                    val radio = RadioButton(this@IconPackListActivity).apply {
                        text = pack.label
                        tag = pack.packageName
                        isChecked = pack.packageName == activePack
                        val padding = (16 * resources.displayMetrics.density).toInt()
                        setPadding(padding, padding, padding, padding)
                    }
                    binding.radioGroup.addView(radio)
                }

                binding.radioGroup.setOnCheckedChangeListener { group, checkedId ->
                    val radio = group.findViewById<RadioButton>(checkedId)
                    val packPkg = radio.tag as? String
                    iconPackManager.setActiveIconPack(packPkg)
                }
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
