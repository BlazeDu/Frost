package com.storm.coldwind.floatwindow

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
import com.storm.coldwind.R
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

    private var floatX = 100
    private var floatY = 200

    private var menuLayoutParams: WindowManager.LayoutParams? = null
    private var dragStartX = 0
    private var dragStartY = 0
    private var dragStartRawX = 0f
    private var dragStartRawY = 0f
    private var isDraggingMenu = false

    private lateinit var pages: MutableList<Menu>
    private var selectedPageId = "page_1"
    private lateinit var rightContentContainer: LinearLayout
    private lateinit var currentColors: ThemeColors

    private fun isDarkMode(): Boolean = ThemeManager.isDarkTheme(applicationContext)

    private fun getThemeColors(): ThemeColors {
        val isDark = isDarkMode()
        return ThemeColors(
            // 深色模式：不透明深色背景；浅色模式：白色背景
            backgroundColor = if (isDark) Color.parseColor("#FF1E1E1E") else Color.parseColor("#FFFFFFFF"),
            surfaceColor = if (isDark) Color.parseColor("#FF2D2D2D") else Color.parseColor("#FFF5F5F5"),
            textColor = if (isDark) Color.WHITE else Color.parseColor("#FF1A1A2E"),
            secondaryTextColor = if (isDark) Color.parseColor("#FFAAAAAA") else Color.parseColor("#FF666666"),
            dividerColor = if (isDark) Color.parseColor("#FF444444") else Color.parseColor("#FFE0E0E0"),
            // 按钮背景色：深色模式用深灰色，浅色模式用浅灰色
            buttonBgColor = if (isDark) Color.parseColor("#FF3D3D3D") else Color.parseColor("#FFE8E8E8"),
            selectedButtonColor = Color.parseColor("#FF3F51B5"),
            toggleOffColor = if (isDark) Color.parseColor("#FF555555") else Color.parseColor("#FFCCCCCC"),
            toggleOnColor = Color.parseColor("#FF3F51B5"),
            // Counter 文字颜色：深色模式白色，浅色模式黑色
            counterTextColor = if (isDark) Color.WHITE else Color.parseColor("#FF000000")
        )
    }

    data class ThemeColors(
        val backgroundColor: Int, val surfaceColor: Int, val textColor: Int,
        val secondaryTextColor: Int, val dividerColor: Int, val buttonBgColor: Int,
        val selectedButtonColor: Int, val toggleOffColor: Int, val toggleOnColor: Int,
        val counterTextColor: Int  // 新增字段
    )

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        ActionDispatcher.init(this, windowManager)
        pages = MenuPages.getPages()
        createNotification()
        createFloatIcon()
        themeListener = {
            handler.post {
                if (isMenuShowing) {
                    menuView?.let { menu ->
                        menu.animate().cancel()
                        menu.visibility = View.GONE
                    }
                    isMenuShowing = false
                    floatIconView?.visibility = View.VISIBLE
                    floatIconView?.alpha = 1f
                    floatIconView?.scaleX = 1f
                    floatIconView?.scaleY = 1f
                }
                if (menuCreated) {
                    safeRemoveView(menuView)
                    menuCreated = false
                    menuView = null
                }
                ensureMenuCreated()
            }
        }
        ThemeManager.addListener(themeListener!!)
    }

    override fun onDestroy() {
        themeListener?.let { ThemeManager.removeListener(it) }
        safeRemoveView(toastView)
        safeRemoveView(floatIconView)
        safeRemoveView(menuView)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    private fun createNotification() {
        val channelId = "float_window_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "悬浮窗服务", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("悬浮窗运行中").setContentText("悬浮窗服务正在运行")
            .setSmallIcon(android.R.drawable.ic_dialog_info).build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(1, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(1, notification)
        }
    }

    private fun createFloatIcon() {
        try {
            windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val container = FrameLayout(this).apply {
                background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Color.WHITE) }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    outlineProvider = ViewOutlineProvider.BACKGROUND
                    clipToOutline = true
                }
            }
            val imageView = ImageView(this).apply {
                setImageDrawable(ContextCompat.getDrawable(this@FloatWindowService, R.drawable.icon))
                scaleType = ImageView.ScaleType.CENTER_CROP
            }
            container.addView(imageView, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
            floatIconView = container

            val size = (55 * resources.displayMetrics.density).toInt()
            val params = WindowManager.LayoutParams(size, size, getWindowType(),
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT)
            params.gravity = Gravity.TOP or Gravity.START
            params.x = floatX
            params.y = floatY

            var startX = 0; var startY = 0; var startRawX = 0f; var startRawY = 0f
            var isDrag = false; var hasMoved = false

            floatIconView?.setOnTouchListener { _, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        startX = params.x; startY = params.y
                        startRawX = event.rawX; startRawY = event.rawY
                        isDrag = false; hasMoved = false
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        if (Math.abs(event.rawX - startRawX) > 10 || Math.abs(event.rawY - startRawY) > 10) {
                            isDrag = true
                            hasMoved = true
                        }
                        if (isDrag) {
                            params.x = startX + (event.rawX - startRawX).toInt()
                            params.y = startY + (event.rawY - startRawY).toInt()
                            windowManager.updateViewLayout(floatIconView, params)
                            floatX = params.x; floatY = params.y
                        }
                        true
                    }
                    MotionEvent.ACTION_UP -> {
                        if (!hasMoved) toggleMenu()
                        true
                    }
                    else -> false
                }
            }
            windowManager.addView(floatIconView, params)
        } catch (e: Exception) { Log.e(TAG, "悬浮球创建失败: ${e.message}") }
    }

    private fun ensureMenuCreated() {
        if (!menuCreated) {
            createMenuView()
            menuCreated = true
        }
    }

    private fun safeRemoveView(view: View?) {
        try {
            view?.let { if (it.parent != null) windowManager.removeView(it) }
        } catch (_: Exception) {}
    }

    private fun toggleMenu() {
        if (isMenuShowing) {
            menuView?.let { menu ->
                menu.animate().cancel()
                menu.animate().alpha(0f).scaleX(0.8f).scaleY(0.8f).translationY(-50f).setDuration(200)
                    .setInterpolator(DecelerateInterpolator()).withEndAction {
                        menu.visibility = View.GONE
                        isMenuShowing = false
                        floatIconView?.visibility = View.VISIBLE
                        floatIconView?.alpha = 1f
                        floatIconView?.animate()?.scaleX(1f)?.scaleY(1f)?.setDuration(100)?.start()
                    }.start()
            }
        } else {
            ensureMenuCreated()
            menuView?.let { menu ->
                menuLayoutParams?.x = floatX
                menuLayoutParams?.y = floatY
                if (menu.parent == null) windowManager.addView(menu, menuLayoutParams)
                else windowManager.updateViewLayout(menu, menuLayoutParams)

                menu.visibility = View.VISIBLE
                menu.alpha = 0f
                menu.scaleX = 0.85f
                menu.scaleY = 0.85f
                menu.translationY = 30f
                menu.pivotX = 0f
                menu.pivotY = 0f

                floatIconView?.animate()?.alpha(0f)?.scaleX(0.5f)?.scaleY(0.5f)?.setDuration(150)
                    ?.withEndAction { floatIconView?.visibility = View.GONE }?.start()

                menu.animate().cancel()
                menu.animate().alpha(1f).scaleX(1f).scaleY(1f).translationY(0f).setDuration(300)
                    .setInterpolator(DecelerateInterpolator()).withEndAction { isMenuShowing = true }.start()
            }
        }
    }

    private fun updateRightContent() {
        rightContentContainer.removeAllViews()
        val page = pages.find { it.id == selectedPageId } ?: return
        val colors = currentColors

        page.items.forEach { item ->
            when (item) {
                is Item.Switch -> {
                    rightContentContainer.addView(createSwitchView(item, colors))
                    rightContentContainer.addView(createDivider(colors))
                }
                is Item.Counter -> {
                    rightContentContainer.addView(createCounterView(item, colors))
                    rightContentContainer.addView(createDivider(colors))
                }
                is Item.Selector -> {
                    rightContentContainer.addView(createSelectorView(item, colors))
                    rightContentContainer.addView(createDivider(colors))
                }
            }
        }
        if (rightContentContainer.childCount > 0) {
            val lastView = rightContentContainer.getChildAt(rightContentContainer.childCount - 1)
            if (lastView is View && lastView.layoutParams?.height == 1) {
                rightContentContainer.removeViewAt(rightContentContainer.childCount - 1)
            }
        }
    }

    private fun createSwitchView(item: Item.Switch, colors: ThemeColors): LinearLayout {
        val density = resources.displayMetrics.density
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, (12 * density).toInt(), 0, (12 * density).toInt())
            isClickable = true
        }

        row.addView(TextView(this).apply {
            text = item.title
            textSize = 14f
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
                setColor(if (item.enabled) colors.toggleOnColor else colors.toggleOffColor)
            }
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
        }
        val toggleThumb = View(this).apply {
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Color.WHITE) }
            val ts = (16 * density).toInt()
            layoutParams = FrameLayout.LayoutParams(ts, ts).apply {
                gravity = Gravity.CENTER_VERTICAL
                leftMargin = if (item.enabled) (14 * density).toInt() else 2
            }
        }
        toggleContainer.addView(toggleBg)
        toggleContainer.addView(toggleThumb)
        row.addView(toggleContainer)

        row.setOnClickListener {
            item.enabled = !item.enabled
            (toggleBg.background as GradientDrawable).setColor(if (item.enabled) colors.toggleOnColor else colors.toggleOffColor)
            (toggleThumb.layoutParams as FrameLayout.LayoutParams).leftMargin = if (item.enabled) (14 * density).toInt() else 2
            toggleThumb.requestLayout()
            ActionDispatcher.execute(item.id, item.enabled)
        }

        return row
    }

    private fun createCounterView(item: Item.Counter, colors: ThemeColors): LinearLayout {
        val density = resources.displayMetrics.density
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, (12 * density).toInt(), 0, (12 * density).toInt())
        }

        row.addView(TextView(this).apply {
            text = item.title
            textSize = 14f
            setTextColor(colors.textColor)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        })

        val counterContainer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }

        val valueText = TextView(this).apply {
            text = item.value.toString()
            textSize = 16f
            setTextColor(colors.textColor)
            setPadding((12 * density).toInt(), 0, (12 * density).toInt(), 0)
        }

        val minusBtn = TextView(this).apply {
            text = "-"
            textSize = 18f
            setTextColor(colors.textColor)  // 改为跟随主题
            gravity = Gravity.CENTER
            setPadding((10 * density).toInt(), (4 * density).toInt(), (10 * density).toInt(), (4 * density).toInt())
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 8 * density
                setColor(colors.buttonBgColor)
            }
            setOnClickListener {
                if (item.value > 0) {
                    item.value--
                    valueText.text = item.value.toString()
                    ActionDispatcher.execute(item.id, item.value)
                }
            }
        }

        val plusBtn = TextView(this).apply {
            text = "+"
            textSize = 18f
            setTextColor(colors.textColor)  // 改为跟随主题
            gravity = Gravity.CENTER
            setPadding((10 * density).toInt(), (4 * density).toInt(), (10 * density).toInt(), (4 * density).toInt())
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 8 * density
                setColor(colors.buttonBgColor)
            }
            setOnClickListener {
                item.value++
                valueText.text = item.value.toString()
                ActionDispatcher.execute(item.id, item.value)
            }
        }

        counterContainer.addView(minusBtn)
        counterContainer.addView(valueText)
        counterContainer.addView(plusBtn)
        row.addView(counterContainer)

        return row
    }

    private fun createSelectorView(item: Item.Selector, colors: ThemeColors): LinearLayout {
        val density = resources.displayMetrics.density
        val isDark = isDarkMode()
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, (8 * density).toInt(), 0, (8 * density).toInt())
        }

        container.addView(TextView(this).apply {
            text = item.title
            textSize = 14f
            setTextColor(colors.secondaryTextColor)
            setPadding(0, 0, 0, (8 * density).toInt())
        })

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 0, 0, 0)
        }

        item.options.forEachIndexed { index, option ->
            val isSelected = item.selected == option
            val btn = TextView(this).apply {
                text = option
                textSize = 12f
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(0, (36 * density).toInt(), 1f)
                setPadding((4 * density).toInt(), (8 * density).toInt(), (4 * density).toInt(), (8 * density).toInt())
                setTextColor(if (isSelected) Color.WHITE else colors.textColor)
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = 8 * density
                    setColor(if (isSelected) colors.selectedButtonColor else colors.buttonBgColor)
                }
                setOnClickListener {
                    item.selected = option
                    for (i in 0 until (parent as LinearLayout).childCount) {
                        val child = (parent as LinearLayout).getChildAt(i)
                        if (child is TextView) {
                            val childSelected = child.text.toString() == option
                            child.setTextColor(if (childSelected) Color.WHITE else colors.textColor)
                            (child.background as GradientDrawable).setColor(
                                if (childSelected) colors.selectedButtonColor else colors.buttonBgColor
                            )
                        }
                    }
                    ActionDispatcher.execute(item.id, option)
                }
            }
            row.addView(btn)
            if (index < item.options.size - 1) {
                row.addView(View(this@FloatWindowService).apply {
                    layoutParams = LinearLayout.LayoutParams((8 * density).toInt(), ViewGroup.LayoutParams.MATCH_PARENT)
                })
            }
        }
        container.addView(row)
        return container
    }

    private fun createDivider(colors: ThemeColors): View {
        val density = resources.displayMetrics.density
        return View(this).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1)
            setBackgroundColor(colors.dividerColor)
            (layoutParams as LinearLayout.LayoutParams).setMargins(0, (8 * density).toInt(), 0, (8 * density).toInt())
        }
    }

    private fun createMenuView() {
        val density = resources.displayMetrics.density
        currentColors = getThemeColors()
        val colors = currentColors

        val dragContainer = FrameLayout(this).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 12 * density
                setColor(colors.backgroundColor)
            }
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
                            val dx = (event.rawX - dragStartRawX).toInt()
                            val dy = (event.rawY - dragStartRawY).toInt()
                            if (Math.abs(dx) > 3 || Math.abs(dy) > 3) isDraggingMenu = true
                            if (isDraggingMenu) {
                                menuLayoutParams!!.x = dragStartX + dx
                                menuLayoutParams!!.y = dragStartY + dy
                                windowManager.updateViewLayout(this, menuLayoutParams)
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

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding((12 * density).toInt(), (12 * density).toInt(), (12 * density).toInt(), (12 * density).toInt())
        }
        header.addView(TextView(this).apply {
            text = "泠风辅助"
            textSize = 18f
            setTextColor(colors.textColor)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        })
        header.addView(TextView(this).apply {
            text = "✕"
            textSize = 20f
            setTextColor(colors.textColor)
            gravity = Gravity.CENTER
            val size = (32 * density).toInt()
            layoutParams = LinearLayout.LayoutParams(size, size)
            setPadding((4 * density).toInt(), (4 * density).toInt(), (4 * density).toInt(), (4 * density).toInt())
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(colors.buttonBgColor) }
            setOnClickListener { toggleMenu() }
        })

        val headerDivider = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1)
            setBackgroundColor(colors.dividerColor)
        }

        val mainRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }

        val leftMenu = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams((80 * density).toInt(), ViewGroup.LayoutParams.MATCH_PARENT)
            setPadding((8 * density).toInt(), (12 * density).toInt(), (4 * density).toInt(), (12 * density).toInt())
        }

        val menuButtons = mutableListOf<View>()

        pages.forEachIndexed { idx, page ->
            if (idx > 0) {
                leftMenu.addView(View(this).apply { layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (8 * density).toInt()) })
            }
            val isSelected = selectedPageId == page.id
            val btn = TextView(this).apply {
                text = page.title
                textSize = 12f
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (44 * density).toInt())
                setTextColor(if (isSelected) Color.WHITE else colors.textColor)
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = 8 * density
                    setColor(if (isSelected) colors.selectedButtonColor else colors.buttonBgColor)
                }
                setOnClickListener {
                    selectedPageId = page.id
                    menuButtons.forEach { b ->
                        val btnView = b as TextView
                        val btnSelected = btnView.text.toString() == page.title
                        btnView.setTextColor(if (btnSelected) Color.WHITE else colors.textColor)
                        (btnView.background as GradientDrawable).setColor(
                            if (btnSelected) colors.selectedButtonColor else colors.buttonBgColor
                        )
                    }
                    updateRightContent()
                }
            }
            leftMenu.addView(btn)
            menuButtons.add(btn)
        }

        val menuDivider = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(1, ViewGroup.LayoutParams.MATCH_PARENT)
            setBackgroundColor(colors.dividerColor)
        }

        rightContentContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f)
            setPadding((12 * density).toInt(), (12 * density).toInt(), (12 * density).toInt(), (12 * density).toInt())
        }

        mainRow.addView(leftMenu)
        mainRow.addView(menuDivider)
        mainRow.addView(rightContentContainer)

        val scrollView = ScrollView(this).apply {
            addView(mainRow)
            isVerticalScrollBarEnabled = false
        }

        val mainContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, (420 * density).toInt())
        }
        mainContainer.addView(header)
        mainContainer.addView(headerDivider)
        mainContainer.addView(scrollView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        dragContainer.addView(mainContainer)
        menuView = dragContainer

        menuLayoutParams = WindowManager.LayoutParams(
            (320 * density).toInt(), (420 * density).toInt(), getWindowType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT
        )
        menuLayoutParams?.gravity = Gravity.TOP or Gravity.START
        menuLayoutParams?.x = floatX
        menuLayoutParams?.y = floatY
        menuView?.visibility = View.GONE

        updateRightContent()
    }

    private fun getWindowType(): Int = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE
}