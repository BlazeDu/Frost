package com.storm.coldwind.floatwindow

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import android.graphics.PixelFormat
import android.graphics.Color
import android.graphics.drawable.GradientDrawable

object ActionDispatcher {

    private lateinit var context: Context
    private lateinit var windowManager: WindowManager
    private val mainHandler = Handler(Looper.getMainLooper())
    private var toastView: View? = null

    fun init(ctx: Context, wm: WindowManager) {
        context = ctx.applicationContext
        windowManager = wm
    }

    fun showFloatingMessage(message: String) {
        mainHandler.post {
            try {
                hideFloatingMessage()

                val density = context.resources.displayMetrics.density

                val messageView = TextView(context).apply {
                    text = message
                    textSize = 14f
                    setTextColor(Color.WHITE)
                    gravity = Gravity.CENTER
                    setPadding((16 * density).toInt(), (10 * density).toInt(), (16 * density).toInt(), (10 * density).toInt())

                    background = GradientDrawable().apply {
                        shape = GradientDrawable.RECTANGLE
                        cornerRadius = 8 * density
                        setColor(Color.parseColor("#E61E1E1E"))
                        setStroke(1, Color.parseColor("#3F51B5"))
                    }
                }

                toastView = messageView

                val params = WindowManager.LayoutParams(
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                    } else {
                        WindowManager.LayoutParams.TYPE_PHONE
                    },
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                    PixelFormat.TRANSLUCENT
                )

                params.gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                params.y = (100 * density).toInt()

                windowManager.addView(messageView, params)

                mainHandler.postDelayed({
                    hideFloatingMessage()
                }, 2000)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun hideFloatingMessage() {
        try {
            toastView?.let {
                windowManager.removeView(it)
                toastView = null
            }
        } catch (e: Exception) {
            // 忽略
        }
    }
}