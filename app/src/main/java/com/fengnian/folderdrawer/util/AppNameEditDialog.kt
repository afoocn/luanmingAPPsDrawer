package com.fengnian.folderdrawer.util

import android.app.Activity
import android.app.Dialog
import android.text.InputType
import android.view.LayoutInflater
import android.view.ViewGroup
import android.view.Window
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import com.fengnian.folderdrawer.R

/**
 * APP 名称编辑弹窗
 *
 * 用法：
 *   AppNameEditDialog.show(activity, currentName, originalName) { newName -> ... }
 *   - newName 为空字符串表示恢复原名
 */
object AppNameEditDialog {

    fun show(
        activity: Activity,
        currentName: String,
        originalName: String,
        onConfirm: (String) -> Unit
    ) {
        val dialog = Dialog(activity)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)

        // 动态构建：标题 + 输入框 + 按钮
        val padding = (20 * activity.resources.displayMetrics.density).toInt()
        val container = android.widget.LinearLayout(activity).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(padding, padding, padding, padding)
        }

        val title = TextView(activity).apply {
            text = "修改名称"
            textSize = 18f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setTextColor(0xFF202124.toInt())
        }
        container.addView(title)

        val subtitle = TextView(activity).apply {
            text = "原名: $originalName"
            textSize = 13f
            setTextColor(0xFF5F6368.toInt())
            setPadding(0, (8 * activity.resources.displayMetrics.density).toInt(), 0, 0)
        }
        container.addView(subtitle)

        val input = EditText(activity).apply {
            inputType = InputType.TYPE_CLASS_TEXT
            setText(currentName)
            setSelection(currentName.length)
            hint = "留空恢复原名"
            setPadding(
                (12 * activity.resources.displayMetrics.density).toInt(),
                (12 * activity.resources.displayMetrics.density).toInt(),
                (12 * activity.resources.displayMetrics.density).toInt(),
                (12 * activity.resources.displayMetrics.density).toInt()
            )
            setBackgroundColor(0xFFF1F3F4.toInt())
        }
        val inputParams = android.widget.LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            topMargin = (16 * activity.resources.displayMetrics.density).toInt()
        }
        container.addView(input, inputParams)

        // 按钮行
        val buttonRow = android.widget.LinearLayout(activity).apply {
            orientation = android.widget.LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.END
        }
        val btnParams = android.widget.LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            marginStart = (8 * activity.resources.displayMetrics.density).toInt()
        }

        val btnCancel = Button(activity).apply {
            text = "取消"
            setOnClickListener { dialog.dismiss() }
        }
        buttonRow.addView(btnCancel, btnParams)

        val btnReset = Button(activity).apply {
            text = "恢复原名"
            setOnClickListener {
                onConfirm("")
                dialog.dismiss()
            }
        }
        buttonRow.addView(btnReset, btnParams)

        val btnOk = Button(activity).apply {
            text = "确定"
            setOnClickListener {
                onConfirm(input.text.toString().trim())
                dialog.dismiss()
            }
        }
        buttonRow.addView(btnOk, btnParams)

        val rowParams = android.widget.LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            topMargin = (20 * activity.resources.displayMetrics.density).toInt()
        }
        container.addView(buttonRow, rowParams)

        dialog.setContentView(container)
        dialog.window?.setLayout(
            (320 * activity.resources.displayMetrics.density).toInt(),
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        dialog.show()
    }
}
