package com.storm.coldwind

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.storm.coldwind.ThemeManager

class FloatWindowService : Service() {

    private lateinit var windowManager: WindowManager
    private var floatIconView: View? = null
    private var menuView: View? = null
    private var isMenuShowing = false
    private val handler = Handler(Looper.getMainLooper())
    private val TAG = "FloatWindowService"
    private var toastView: View? = null

    private var menuCreated = false
    private var themeListener: (() -> Unit)? = null

    // 悬浮球位置
    private var floatX = 100
    private var floatY = 200
    private val floatSize = 55

    // 菜单拖拽
    private var menuLayoutParams: WindowManager.LayoutParams? = null
    private var dragStartX = 0
    private var dragStartY = 0
    private var dragStartRawX = 0f
    private var dragStartRawY = 0f
    private var isDraggingMenu = false

    // 打开默认选中的菜单
    private var selectedMenu = "m1"

    // 菜单项的状态
    private val themeSetting = mutableStateOf("星空")
    private val offlineMode = mutableStateOf(false)
    private val infiniteEnergy = mutableStateOf(false)
    private val gameSpeed = mutableStateOf("默认")
    private val wingCount = mutableStateOf(0)
    private val flightBoost = mutableStateOf(0)
    private val jumpBoost = mutableStateOf(0)
    private val menu4Value = mutableStateOf(0)
    private val menu5Value = mutableStateOf(0)

    private class MutableState<T>(initialValue: T) {
        var value: T = initialValue
    }

    private fun <T> mutableStateOf(initialValue: T): MutableState<T> = MutableState(initialValue)

    private fun isDarkMode(): Boolean {
        return ThemeManager.isDarkTheme(applicationContext)
    }

    private fun getThemeColors(): ThemeColors {
        val isDark = isDarkMode()
        return ThemeColors(
            backgroundColor = if (isDark) Color.parseColor("#E61E1E1E") else Color.parseColor("#FFFFFFFF"),
            surfaceColor = if (isDark) Color.parseColor("#2A2A2A") else Color.parseColor("#F5F5F5"),
            textColor = if (isDark) Color.WHITE else Color.parseColor("#1A1A2E"),
            secondaryTextColor = if (isDark) Color.parseColor("#AAAAAA") else Color.parseColor("#666666"),
            dividerColor = if (isDark) Color.parseColor("#333333") else Color.parseColor("#E0E0E0"),
            buttonBgColor = if (isDark) Color.parseColor("#333333") else Color.parseColor("#E8E8E8"),
            selectedButtonColor = Color.parseColor("#3F51B5"),
            toggleOffColor = Color.parseColor("#666666"),
            toggleOnColor = Color.parseColor("#3F51B5")
        )
    }

    data class ThemeColors(
        val backgroundColor: Int,
        val surfaceColor: Int,
        val textColor: Int,
        val secondaryTextColor: Int,
        val dividerColor: Int,
        val buttonBgColor: Int,
        val selectedButtonColor: Int,
        val toggleOffColor: Int,
        val toggleOnColor: Int
    )

    private fun showFloatingMessage(message: String) {
        handler.post {
            try {
                hideFloatingMessage()
                val density = resources.displayMetrics.density
                val colors = getThemeColors()
                val messageView = TextView(this).apply {
                    text = message
                    textSize = 14f
                    setTextColor(colors.textColor)
                    gravity = Gravity.CENTER
                    setPadding((16 * density).toInt(), (10 * density).toInt(), (16 * density).toInt(), (10 * density).toInt())
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.RECTANGLE
                        cornerRadius = 8 * density
                        setColor(colors.backgroundColor)
                        setStroke(1, colors.selectedButtonColor)
                    }
                }
                toastView = messageView
                val params = WindowManager.LayoutParams(
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    getWindowType(),
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                    PixelFormat.TRANSLUCENT
                )
                params.gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                params.y = (100 * density).toInt()
                windowManager.addView(messageView, params)
                handler.postDelayed({ hideFloatingMessage() }, 2000)
            } catch (e: Exception) {
                Log.e(TAG, "显示悬浮提示失败: ${e.message}")
            }
        }
    }

    private fun hideFloatingMessage() {
        try {
            toastView?.let {
                windowManager.removeView(it)
                toastView = null
            }
        } catch (e: Exception) { }
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "========== Service onCreate ==========")
        createNotification()
        createFloatIcon()

        // 监听主题变化
        themeListener = {
            handler.post {
                recreateMenu()
            }
        }
        ThemeManager.addListener(themeListener!!)
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "========== Service onDestroy ==========")
        // 移除监听器
        themeListener?.let {
            ThemeManager.removeListener(it)
        }
        try {
            hideFloatingMessage()
            floatIconView?.let { windowManager.removeView(it) }
            menuView?.let { if (it.parent != null) windowManager.removeView(it) }
        } catch (e: Exception) { e.printStackTrace() }
    }

    // 重新创建菜单
    private fun recreateMenu() {
        if (menuCreated) {
            // 如果菜单已显示，先关闭
            if (isMenuShowing) {
                menuView?.visibility = View.GONE
                isMenuShowing = false
                floatIconView?.visibility = View.VISIBLE
                floatIconView?.alpha = 1f
            }
            // 移除旧菜单
            menuView?.let {
                if (it.parent != null) {
                    windowManager.removeView(it)
                }
            }
            menuCreated = false
            menuView = null
            // 重新创建
            ensureMenuCreated()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand")
        return START_STICKY
    }

    private fun createNotification() {
        val channelId = "float_window_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "悬浮窗服务", NotificationManager.IMPORTANCE_LOW)
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("悬浮窗运行中")
            .setContentText("悬浮窗服务正在运行")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(1, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(1, notification)
        }
    }

    private fun createFloatIcon() {
        try {
            Log.d(TAG, "createFloatIcon: 开始创建悬浮球")
            windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager

            val container = FrameLayout(this).apply {
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(Color.WHITE)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    outlineProvider = ViewOutlineProvider.BACKGROUND
                    clipToOutline = true
                }
            }

            val imageView = ImageView(this).apply {
                val drawable = ContextCompat.getDrawable(this@FloatWindowService, R.drawable.icon)
                setImageDrawable(drawable)
                scaleType = ImageView.ScaleType.CENTER_CROP
            }

            container.addView(imageView, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            ))

            floatIconView = container

            val size = (55 * resources.displayMetrics.density).toInt()
            val layoutParams = WindowManager.LayoutParams(
                size, size,
                getWindowType(),
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
            )

            layoutParams.gravity = Gravity.TOP or Gravity.START
            layoutParams.x = floatX
            layoutParams.y = floatY

            var initialX = 0
            var initialY = 0
            var initialTouchX = 0f
            var initialTouchY = 0f
            var isDragging = false
            var hasMoved = false  // 新增：标记是否移动过

            floatIconView?.setOnTouchListener { _, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = layoutParams.x
                        initialY = layoutParams.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        isDragging = false
                        hasMoved = false
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val deltaX = Math.abs(event.rawX - initialTouchX)
                        val deltaY = Math.abs(event.rawY - initialTouchY)
                        // 移动超过10像素认为是拖拽
                        if (deltaX > 10 || deltaY > 10) {
                            isDragging = true
                            hasMoved = true
                        }
                        if (isDragging) {
                            layoutParams.x = initialX + (event.rawX - initialTouchX).toInt()
                            layoutParams.y = initialY + (event.rawY - initialTouchY).toInt()
                            windowManager.updateViewLayout(floatIconView, layoutParams)
                            floatX = layoutParams.x
                            floatY = layoutParams.y
                        }
                        true
                    }
                    MotionEvent.ACTION_UP -> {
                        // 只有没有移动过（没有拖拽）时才打开菜单
                        if (!hasMoved) {
                            toggleMenu()
                        }
                        true
                    }
                    else -> false
                }
            }

            windowManager.addView(floatIconView, layoutParams)
            Log.d(TAG, "悬浮球创建成功")
        } catch (e: Exception) {
            Log.e(TAG, "Error: ${e.message}", e)
        }
    }

    // 预创建菜单（只创建一次）
    private fun ensureMenuCreated() {
        if (!menuCreated) {
            createMenuView()
            menuCreated = true
        }
    }

    private fun toggleMenu() {
        Log.d(TAG, "toggleMenu: isMenuShowing=$isMenuShowing")

        if (isMenuShowing) {
            // 关闭菜单 - iOS 风格动画
            menuView?.let { menu ->
                menu.animate().cancel()

                // iOS 关闭动画：淡出 + 缩小 + 轻微上移
                menu.animate()
                    .alpha(0f)
                    .scaleX(0.8f)
                    .scaleY(0.8f)
                    .translationY(-50f)
                    .setDuration(200)
                    .setInterpolator(DecelerateInterpolator())
                    .withEndAction {
                        menu.visibility = View.GONE
                        isMenuShowing = false
                        floatIconView?.visibility = View.VISIBLE
                        floatIconView?.alpha = 1f
                        // 悬浮球轻微弹入动画
                        floatIconView?.animate()
                            ?.scaleX(1f)
                            ?.scaleY(1f)
                            ?.setDuration(100)
                            ?.start()
                    }
                    .start()
            }
        } else {
            // 打开菜单 - iOS 风格动画
            ensureMenuCreated()

            menuView?.let { menu ->
                // 更新位置
                menuLayoutParams?.x = floatX
                menuLayoutParams?.y = floatY
                if (menu.parent == null) {
                    windowManager.addView(menu, menuLayoutParams)
                } else {
                    windowManager.updateViewLayout(menu, menuLayoutParams)
                }

                // 初始状态：透明、缩小、略微下移
                menu.visibility = View.VISIBLE
                menu.alpha = 0f
                menu.scaleX = 0.85f
                menu.scaleY = 0.85f
                menu.translationY = 30f
                menu.pivotX = 0f
                menu.pivotY = 0f

                // 隐藏悬浮球并添加淡出动画
                floatIconView?.animate()
                    ?.alpha(0f)
                    ?.scaleX(0.5f)
                    ?.scaleY(0.5f)
                    ?.setDuration(150)
                    ?.withEndAction {
                        floatIconView?.visibility = View.GONE
                    }
                    ?.start()

                // iOS 打开动画：淡入 + 放大 + 回弹
                menu.animate().cancel()
                menu.animate()
                    .alpha(1f)
                    .scaleX(1f)
                    .scaleY(1f)
                    .translationY(0f)
                    .setDuration(300)
                    .setInterpolator(DecelerateInterpolator())
                    .withEndAction {
                        isMenuShowing = true
                    }
                    .start()
            }
        }
    }

    private fun createMenuView() {
        Log.d(TAG, "createMenuView: 开始创建菜单布局")
        val density = resources.displayMetrics.density
        val colors = getThemeColors()

        val dragContainer = FrameLayout(this).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 12 * density
                setColor(colors.backgroundColor)
            }
        }

        val fixedHeader = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding((12 * density).toInt(), (8 * density).toInt(), (12 * density).toInt(), (8 * density).toInt())
            setOnTouchListener { _, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        if (menuLayoutParams != null) {
                            dragStartX = menuLayoutParams!!.x
                            dragStartY = menuLayoutParams!!.y
                            dragStartRawX = event.rawX
                            dragStartRawY = event.rawY
                            isDraggingMenu = false
                        }
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        if (menuLayoutParams != null) {
                            val deltaX = (event.rawX - dragStartRawX).toInt()
                            val deltaY = (event.rawY - dragStartRawY).toInt()
                            if (Math.abs(deltaX) > 3 || Math.abs(deltaY) > 3) isDraggingMenu = true
                            if (isDraggingMenu) {
                                menuLayoutParams!!.x = dragStartX + deltaX
                                menuLayoutParams!!.y = dragStartY + deltaY
                                windowManager.updateViewLayout(dragContainer, menuLayoutParams)
                                floatX = menuLayoutParams!!.x
                                floatY = menuLayoutParams!!.y
                            }
                        }
                        true
                    }
                    MotionEvent.ACTION_UP -> { isDraggingMenu = false; true }
                    else -> false
                }
            }
        }

        val titleText = TextView(this).apply {
            text = "泠风辅助"
            textSize = 15f
            setTextColor(colors.textColor)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
        fixedHeader.addView(titleText)

        val closeButton = TextView(this).apply {
            text = "✕"
            textSize = 16f
            setTextColor(colors.textColor)
            gravity = Gravity.CENTER
            val size = (28 * density).toInt()
            layoutParams = LinearLayout.LayoutParams(size, size)
            setPadding((4 * density).toInt(), (4 * density).toInt(), (4 * density).toInt(), (4 * density).toInt())
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(colors.buttonBgColor)
            }
            setOnClickListener {
                toggleMenu()
            }
        }
        fixedHeader.addView(closeButton)

        val headerDivider = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1)
            setBackgroundColor(colors.dividerColor)
        }

        val scrollContent = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }

        val leftMenu = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams((80 * density).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT)
            setPadding((8 * density).toInt(), (8 * density).toInt(), (4 * density).toInt(), (8 * density).toInt())
        }

        val rightContent = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            setPadding((8 * density).toInt(), (6 * density).toInt(), (6 * density).toInt(), (6 * density).toInt())
        }

        val menus = listOf("m1", "m2", "m3", "m4")
        val menuButtons = mutableListOf<TextView>()

        menus.forEachIndexed { index, menuName ->
            if (index > 0) {
                leftMenu.addView(View(this).apply {
                    layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (8 * density).toInt())
                })
            }
            val menuButton = TextView(this).apply {
                text = menuName.uppercase()
                textSize = 12f
                setTextColor(colors.textColor)
                gravity = Gravity.CENTER
                val height = (40 * density).toInt()
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, height)
                setPadding(0, (8 * density).toInt(), 0, (8 * density).toInt())
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = 6 * density
                    setColor(if (selectedMenu == menuName) colors.selectedButtonColor else colors.buttonBgColor)
                }
                setOnClickListener {
                    animateClick(this)
                    switchMenu(menuName, rightContent, menuButtons, colors)
                }
            }
            leftMenu.addView(menuButton)
            menuButtons.add(menuButton)
        }

        updateRightContent(rightContent, colors)

        scrollContent.addView(leftMenu)
        scrollContent.addView(FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(1, ViewGroup.LayoutParams.MATCH_PARENT)
            setBackgroundColor(colors.dividerColor)
        })
        scrollContent.addView(rightContent)

        val scrollView = ScrollView(this).apply {
            setPadding(0, 0, 0, 0)
            isVerticalScrollBarEnabled = false
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            addView(scrollContent)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        }

        val mainContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, (380 * density).toInt())
        }
        mainContainer.addView(fixedHeader)
        mainContainer.addView(headerDivider)
        mainContainer.addView(scrollView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        dragContainer.addView(mainContainer)
        menuView = dragContainer

        menuLayoutParams = WindowManager.LayoutParams(
            (320 * density).toInt(),
            (380 * density).toInt(),
            getWindowType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )
        menuLayoutParams?.gravity = Gravity.TOP or Gravity.START
        menuLayoutParams?.x = floatX
        menuLayoutParams?.y = floatY

        // 初始状态隐藏
        menuView?.visibility = View.GONE

        Log.d(TAG, "createMenuView: 菜单布局创建完成")
    }

    private fun switchMenu(menuName: String, rightContent: FrameLayout, menuButtons: List<TextView>, colors: ThemeColors) {
        selectedMenu = menuName
        menuButtons.forEach { button ->
            (button.background as GradientDrawable).setColor(
                if (button.text.toString().lowercase() == menuName) colors.selectedButtonColor else colors.buttonBgColor
            )
        }
        val oldContent = rightContent.getChildAt(0)
        val newContent = createContentForMenu(menuName, colors)
        newContent.alpha = 0f
        newContent.translationX = 30f
        rightContent.addView(newContent)
        newContent.animate().alpha(1f).translationX(0f).setDuration(200).setInterpolator(DecelerateInterpolator()).start()
        if (oldContent != null) {
            oldContent.animate().alpha(0f).translationX(-30f).setDuration(150).withEndAction {
                rightContent.removeView(oldContent)
            }.start()
        }
    }

    private fun createContentForMenu(menuName: String, colors: ThemeColors): View {
        val density = resources.displayMetrics.density
        val scrollView = ScrollView(this).apply {
            isVerticalScrollBarEnabled = false
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
        }
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }

        when (menuName) {
            "m1" -> {
                container.addView(createSectionTitle("主题设置", colors.secondaryTextColor))
                val themeRow = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    setPadding(0, (4 * density).toInt(), 0, (4 * density).toInt())
                }
                themeRow.addView(createThemeButton("星空", "☁️", colors))
                themeRow.addView(createThemeButton("雨天", "🌧️", colors))
                themeRow.addView(createThemeButton("雪天", "❄️", colors))
                container.addView(themeRow)
                container.addView(createDivider(colors.dividerColor))
                container.addView(createSectionTitle("DKTool", colors.secondaryTextColor))
                container.addView(createSwitchItem("离线模式", offlineMode, colors))
                container.addView(createSwitchItem("无限能量", infiniteEnergy, colors))
            }
            "m2" -> {
                container.addView(createSectionTitle("游戏倍速", colors.secondaryTextColor))
                val speedRow = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    setPadding(0, (4 * density).toInt(), 0, (4 * density).toInt())
                }
                listOf("默认", "二倍", "四倍", "八倍").forEach { speed ->
                    speedRow.addView(createSpeedButton(speed, colors))
                }
                container.addView(speedRow)
                container.addView(createDivider(colors.dividerColor))
                container.addView(createCounterItem("光翼数量", wingCount, colors))
                container.addView(createDivider(colors.dividerColor))
                container.addView(createCounterItem("飞行强化", flightBoost, colors))
            }
            "m3" -> {
                container.addView(createCounterItem("跳跃强化", jumpBoost, colors))
                container.addView(createDivider(colors.dividerColor))
                container.addView(createCounterItem("菜单四", menu4Value, colors))
                container.addView(createDivider(colors.dividerColor))
                container.addView(createCounterItem("菜单五", menu5Value, colors))
            }
            "m4" -> {
                container.addView(createSectionTitle("其他功能", colors.secondaryTextColor))
                container.addView(createSwitchItem("功能1", mutableStateOf(false), colors))
                container.addView(createSwitchItem("功能2", mutableStateOf(false), colors))
                container.addView(createDivider(colors.dividerColor))
                container.addView(createCounterItem("自定义1", mutableStateOf(0), colors))
                container.addView(createDivider(colors.dividerColor))
                container.addView(createCounterItem("自定义2", mutableStateOf(0), colors))
            }
        }
        scrollView.addView(container)
        return scrollView
    }

    private fun updateRightContent(rightContent: FrameLayout, colors: ThemeColors) {
        rightContent.addView(createContentForMenu(selectedMenu, colors))
    }

    private fun createSectionTitle(text: String, textColor: Int): TextView {
        val density = resources.displayMetrics.density
        return TextView(this).apply {
            this.text = text
            textSize = 12f
            setTextColor(textColor)
            setPadding(0, (6 * density).toInt(), 0, (2 * density).toInt())
        }
    }

    private fun createThemeButton(name: String, icon: String, colors: ThemeColors): View {
        val density = resources.displayMetrics.density
        val button = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            setPadding((4 * density).toInt(), (6 * density).toInt(), (4 * density).toInt(), (6 * density).toInt())
            setOnClickListener {
                themeSetting.value = name
                updateThemeSetting(name)
                animateClick(this)
            }
        }
        button.addView(TextView(this).apply { text = icon; textSize = 18f; gravity = Gravity.CENTER })
        button.addView(TextView(this).apply { text = name; textSize = 10f; setTextColor(colors.textColor); gravity = Gravity.CENTER })
        return button
    }

    private fun createSpeedButton(speed: String, colors: ThemeColors): View {
        val density = resources.displayMetrics.density
        val button = TextView(this).apply {
            text = speed
            textSize = 10f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            setPadding((4 * density).toInt(), (6 * density).toInt(), (4 * density).toInt(), (6 * density).toInt())
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 6 * density
                setColor(if (gameSpeed.value == speed) colors.selectedButtonColor else colors.buttonBgColor)
            }
            setOnClickListener {
                gameSpeed.value = speed
                updateSpeedSetting(speed)
                animateClick(this)
                val parentLayout = parent as? LinearLayout
                for (i in 0 until (parentLayout?.childCount ?: 0)) {
                    val child = parentLayout?.getChildAt(i)
                    if (child is TextView) {
                        val bg = child.background as GradientDrawable
                        bg.setColor(if (gameSpeed.value == child.text.toString()) colors.selectedButtonColor else colors.buttonBgColor)
                    }
                }
            }
        }
        return button
    }

    private fun createSwitchItem(title: String, state: MutableState<Boolean>, colors: ThemeColors): View {
        val density = resources.displayMetrics.density
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, (8 * density).toInt(), 0, (8 * density).toInt())
        }
        container.addView(TextView(this).apply {
            text = title
            textSize = 12f
            setTextColor(colors.textColor)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        })
        val toggleContainer = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams((32 * density).toInt(), (20 * density).toInt())
        }
        val toggleBg = View(this).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 10 * density
                setColor(if (state.value) colors.toggleOnColor else colors.toggleOffColor)
            }
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
        }
        val toggleThumb = View(this).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.WHITE)
            }
            val thumbSize = (16 * density).toInt()
            layoutParams = FrameLayout.LayoutParams(thumbSize, thumbSize).apply {
                gravity = Gravity.CENTER_VERTICAL
                leftMargin = if (state.value) (14 * density).toInt() else 2
            }
        }
        toggleContainer.addView(toggleBg)
        toggleContainer.addView(toggleThumb)
        container.addView(toggleContainer)
        container.setOnClickListener {
            state.value = !state.value
            (toggleThumb.layoutParams as FrameLayout.LayoutParams).leftMargin = if (state.value) (14 * density).toInt() else 2
            toggleThumb.requestLayout()
            (toggleBg.background as GradientDrawable).setColor(if (state.value) colors.toggleOnColor else colors.toggleOffColor)
            animateClick(container)
            showFloatingMessage("$title: ${if (state.value) "开启" else "关闭"}")
        }
        return container
    }

    private fun createCounterItem(title: String, valueState: MutableState<Int>, colors: ThemeColors): View {
        val density = resources.displayMetrics.density
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, (8 * density).toInt(), 0, (8 * density).toInt())
        }
        container.addView(TextView(this).apply {
            text = title
            textSize = 12f
            setTextColor(colors.textColor)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        })
        val counterContainer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        val minusBtn = createCounterButton("-", colors)
        val valueText = TextView(this).apply {
            text = valueState.value.toString()
            textSize = 14f
            setTextColor(colors.textColor)
            setPadding((10 * density).toInt(), 0, (10 * density).toInt(), 0)
        }
        val plusBtn = createCounterButton("+", colors)
        counterContainer.addView(minusBtn)
        counterContainer.addView(valueText)
        counterContainer.addView(plusBtn)
        minusBtn.setOnClickListener {
            if (valueState.value > 0) {
                valueState.value--
                valueText.text = valueState.value.toString()
                animateClick(minusBtn)
                showFloatingMessage("$title: ${valueState.value}")
            }
        }
        plusBtn.setOnClickListener {
            valueState.value++
            valueText.text = valueState.value.toString()
            animateClick(plusBtn)
            showFloatingMessage("$title: ${valueState.value}")
        }
        container.addView(counterContainer)
        return container
    }

    private fun createCounterButton(text: String, colors: ThemeColors): TextView {
        val density = resources.displayMetrics.density
        return TextView(this).apply {
            this.text = text
            textSize = 14f
            setTextColor(colors.textColor)
            gravity = Gravity.CENTER
            setPadding((8 * density).toInt(), (2 * density).toInt(), (8 * density).toInt(), (2 * density).toInt())
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 6 * density
                setColor(colors.buttonBgColor)
            }
        }
    }

    private fun createDivider(color: Int): View {
        val density = resources.displayMetrics.density
        val divider = View(this)
        divider.setBackgroundColor(color)
        val params = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1)
        params.setMargins(0, (6 * density).toInt(), 0, (6 * density).toInt())
        divider.layoutParams = params
        return divider
    }

    private fun animateClick(view: View) {
        view.animate().scaleX(0.95f).scaleY(0.95f).setDuration(80).withEndAction {
            view.animate().scaleX(1f).scaleY(1f).setDuration(80).start()
        }.start()
    }

    private fun updateThemeSetting(theme: String) { showFloatingMessage("主题已切换为: $theme") }
    private fun updateSpeedSetting(speed: String) { showFloatingMessage("游戏倍速: $speed") }

    private fun getWindowType(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            WindowManager.LayoutParams.TYPE_PHONE
        }
    }
}