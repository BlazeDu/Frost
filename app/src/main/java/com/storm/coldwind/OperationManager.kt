package com.storm.coldwind

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

object OperationManager {

    private lateinit var context: Context
    private lateinit var windowManager: WindowManager
    private val mainHandler = Handler(Looper.getMainLooper())
    private var toastView: View? = null

    fun init(ctx: Context, wm: WindowManager) {
        context = ctx.applicationContext
        windowManager = wm
    }

    private fun showFloatingMessage(message: String) {
        mainHandler.post {
            try {
                // 移除之前的提示
                hideFloatingMessage()

                val density = context.resources.displayMetrics.density

                // 创建提示视图
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

                // 2秒后自动消失
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

    fun execute(id: String, value: Any) {
        when (id) {
            // 功能1
            "offline_mode" -> offlineMode(value as Boolean)
            "infinite_energy" -> infiniteEnergy(value as Boolean)

            // 功能2
            "game_speed" -> gameSpeed(value as String)
            "wing_count" -> wingCount(value as Int)
            "flight_boost" -> flightBoost(value as Int)

            // 功能3
            "jump_boost" -> jumpBoost(value as Int)
            "function3" -> function3(value as Int)

            // 功能4
            "function4_1" -> function4_1(value as Int)
            "function4_2" -> function4_2(value as Int)
            "function4_switch" -> function4Switch(value as Boolean)
        }
    }

    // ==================== 功能1 ====================
    private fun offlineMode(enabled: Boolean) {
        showFloatingMessage("离线模式: ${if (enabled) "开启" else "关闭"}")
    }

    private fun infiniteEnergy(enabled: Boolean) {
        showFloatingMessage("无限能量: ${if (enabled) "开启" else "关闭"}")
    }

    // ==================== 功能2 ====================
    private fun gameSpeed(speed: String) {
        showFloatingMessage("游戏倍速: $speed")
    }

    private fun wingCount(count: Int) {
        showFloatingMessage("光翼数量: $count")
    }

    private fun flightBoost(value: Int) {
        showFloatingMessage("飞行强化: $value")
    }

    // ==================== 功能3 ====================
    private fun jumpBoost(value: Int) {
        showFloatingMessage("跳跃强化: $value")
    }

    private fun function3(value: Int) {
        showFloatingMessage("功能3: $value")
    }

    // ==================== 功能4 ====================
    private fun function4_1(value: Int) {
        showFloatingMessage("功能4-1: $value")
    }

    private fun function4_2(value: Int) {
        showFloatingMessage("功能4-2: $value")
    }

    private fun function4Switch(enabled: Boolean) {
        showFloatingMessage("功能4开关: ${if (enabled) "开启" else "关闭"}")
    }
}