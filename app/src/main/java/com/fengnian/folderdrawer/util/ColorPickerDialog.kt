package com.fengnian.folderdrawer.util

import android.app.Activity
import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.widget.SeekBar
import android.widget.TextView
import com.fengnian.folderdrawer.R

/**
 * ARGB 颜色选择器
 * 圆角卡片样式弹窗，4 个独立滑块（Alpha / Red / Green / Blue），
 * 滑轨为动态渐变色，顶部预览色块 + hex 代码
 *
 * 用法：ColorPickerDialog.show(activity, initialColor) { color -> ... }
 * 返回 0 表示"默认/自动"
 */
object ColorPickerDialog {

    fun show(
        activity: Activity,
        initialColor: Int,
        onColorPicked: (Int) -> Unit
    ) {
        val dialog = Dialog(activity, android.R.style.Theme_Translucent_NoTitleBar)
        val view = LayoutInflater.from(activity).inflate(R.layout.dialog_color_picker, null)

        val preview = view.findViewById<View>(R.id.colorPreview)
        val hexLabel = view.findViewById<TextView>(R.id.hexLabel)

        val seekAlpha = view.findViewById<SeekBar>(R.id.seekAlpha)
        val seekRed = view.findViewById<SeekBar>(R.id.seekRed)
        val seekGreen = view.findViewById<SeekBar>(R.id.seekGreen)
        val seekBlue = view.findViewById<SeekBar>(R.id.seekBlue)

        val labelRed = view.findViewById<TextView>(R.id.labelRed)
        val labelGreen = view.findViewById<TextView>(R.id.labelGreen)
        val labelBlue = view.findViewById<TextView>(R.id.labelBlue)

        val trackAlpha = view.findViewById<View>(R.id.trackAlpha)
        val trackRed = view.findViewById<View>(R.id.trackRed)
        val trackGreen = view.findViewById<View>(R.id.trackGreen)
        val trackBlue = view.findViewById<View>(R.id.trackBlue)

        val btnCancel = view.findViewById<TextView>(R.id.btnCancel)
        val btnDone = view.findViewById<TextView>(R.id.btnDone)
        val btnDefault = view.findViewById<TextView>(R.id.btnDefault)

        // 解析初始颜色
        val initA: Int
        val initR: Int
        val initG: Int
        val initB: Int
        if (initialColor == 0) {
            initA = 255; initR = 128; initG = 128; initB = 128
        } else {
            initA = Color.alpha(initialColor)
            initR = Color.red(initialColor)
            initG = Color.green(initialColor)
            initB = Color.blue(initialColor)
        }

        seekAlpha.progress = initA
        seekRed.progress = initR
        seekGreen.progress = initG
        seekBlue.progress = initB

        val density = activity.resources.displayMetrics.density
        val trackHeight = (6 * density).toInt()

        fun updateUI() {
            val a = seekAlpha.progress
            val r = seekRed.progress
            val g = seekGreen.progress
            val b = seekBlue.progress

            // 预览色块
            val color = Color.argb(a, r, g, b)
            val gd = GradientDrawable().apply {
                cornerRadius = trackHeight.toFloat()
                setColor(color)
            }
            preview.background = gd

            // hex 标签
            val hex = String.format("#%08X", color)
            hexLabel.text = hex
            // 根据预览色亮度选择文字颜色
            val luminance = (0.299 * r + 0.587 * g + 0.114 * b) / 255.0
            hexLabel.setTextColor(if (luminance > 0.5 && a > 80) Color.BLACK else Color.WHITE)

            // 数值标?            labelAlpha.text = a.toString()
            labelRed.text = r.toString()
            labelGreen.text = g.toString()
            labelBlue.text = b.toString()

            // Alpha 轨道：从透明到当?RGB 全不透明
            val alphaTrack = GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                intArrayOf(
                    Color.argb(0, r, g, b),
                    Color.argb(255, r, g, b)
                )
            ).apply { cornerRadius = trackHeight.toFloat() }
            trackAlpha.background = alphaTrack

            // Red 轨道：从 R=0 ?R=255，使用当?GBA
            val redTrack = GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                intArrayOf(
                    Color.argb(a, 0, g, b),
                    Color.argb(a, 255, g, b)
                )
            ).apply { cornerRadius = trackHeight.toFloat() }
            trackRed.background = redTrack

            // Green 轨道
            val greenTrack = GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                intArrayOf(
                    Color.argb(a, r, 0, b),
                    Color.argb(a, r, 255, b)
                )
            ).apply { cornerRadius = trackHeight.toFloat() }
            trackGreen.background = greenTrack

            // Blue 轨道
            val blueTrack = GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                intArrayOf(
                    Color.argb(a, r, g, 0),
                    Color.argb(a, r, g, 255)
                )
            ).apply { cornerRadius = trackHeight.toFloat() }
            trackBlue.background = blueTrack
        }

        val listener = object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                updateUI()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        }

        seekAlpha.setOnSeekBarChangeListener(listener)
        seekRed.setOnSeekBarChangeListener(listener)
        seekGreen.setOnSeekBarChangeListener(listener)
        seekBlue.setOnSeekBarChangeListener(listener)

        updateUI()

        btnCancel.setOnClickListener { dialog.dismiss() }
        btnDefault.setOnClickListener {
            onColorPicked(0)
            dialog.dismiss()
        }
        btnDone.setOnClickListener {
            val picked = Color.argb(
                seekAlpha.progress,
                seekRed.progress,
                seekGreen.progress,
                seekBlue.progress
            )
            onColorPicked(picked)
            dialog.dismiss()
        }

        dialog.setContentView(view)
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setLayout(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            setGravity(Gravity.CENTER)
            // 关闭暗色遮罩，让弹窗更轻盈
            setDimAmount(0.25f)
            addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        }
        dialog.show()
    }
}
