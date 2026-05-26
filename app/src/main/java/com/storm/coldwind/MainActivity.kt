package com.storm.coldwind

import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.util.Log
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import com.storm.coldwind.floatwindow.FloatWindowService
import com.storm.coldwind.ui.theme.AppTheme
import java.io.DataOutputStream

class MainActivity : ComponentActivity() {

    private val TAG = "MainActivity"
    private var isTabletDevice = false

    // 注册悬浮窗权限请求结果回调
    private val overlayPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (Settings.canDrawOverlays(this)) {
            startFloatService()
        } else {
            Toast.makeText(this, "需要悬浮窗权限才能使用此功能", Toast.LENGTH_LONG).show()
        }
    }

    // 注册文件管理权限请求结果回调（Android 11+）
    private val manageStoragePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (Environment.isExternalStorageManager()) {
                Toast.makeText(this, "文件管理权限已获取", Toast.LENGTH_SHORT).show()
                Log.d(TAG, "文件管理权限已获取")
            } else {
                Toast.makeText(this, "需要文件管理权限才能正常使用", Toast.LENGTH_LONG).show()
            }
        }
    }

    // 注册普通存储权限请求结果回调（Android 6-10）
    private val storagePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (allGranted) {
            Toast.makeText(this, "存储权限已获取", Toast.LENGTH_SHORT).show()
            Log.d(TAG, "存储权限已获取")
        } else {
            Toast.makeText(this, "部分存储权限未获取，可能影响功能", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "onCreate")

        // 先判断设备类型
        isTabletDevice = isTablet()

        // 设置屏幕方向（必须在 setContentView/setContent 之前）
        setRequestedOrientation()

        // 设置全屏和透明状态栏
        setupWindowFlags()

        // 检查并申请文件管理权限
        checkAndRequestManageStoragePermission()

        // 检查并自动申请 Root 权限
        checkAndRequestRootPermission()

        setContent {
            AppTheme {
                // 使用 ThemeManager 获取主题状态
                val isDark = ThemeManager.isDarkTheme(this)

                // 设置状态栏和导航栏颜色跟随主题
                val view = LocalView.current
                SideEffect {
                    val window = (view.context as androidx.activity.ComponentActivity).window

                    // 设置状态栏透明
                    WindowCompat.setDecorFitsSystemWindows(window, false)
                    val insetsController = WindowCompat.getInsetsController(window, view)

                    // 设置状态栏图标颜色（深色模式用白色，浅色模式用深色）
                    insetsController.isAppearanceLightStatusBars = !isDark
                    insetsController.isAppearanceLightNavigationBars = !isDark

                    // 设置状态栏和导航栏背景透明
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        window.statusBarColor = android.graphics.Color.TRANSPARENT
                        window.navigationBarColor = android.graphics.Color.TRANSPARENT
                    }
                }

                MainPage(
                    isDark = isDark,
                    onStartFloat = {
                        checkOverlayPermissionAndStart()
                    },
                    onOpenSettings = {
                        val intent = Intent(this, AppSettings::class.java)
                        startActivity(intent)
                    },
                    onRequestManageStorage = {
                        requestManageStoragePermission()
                    }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // 每次 resume 时再次确保屏幕方向正确
        setRequestedOrientation()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // 配置变化时重新设置屏幕方向
        setRequestedOrientation()
    }

    /**
     * 判断设备是否为平板
     * 通过最小宽度 >= 600dp 判断为平板
     */
    private fun isTablet(): Boolean {
        val smallestWidthDp = resources.configuration.smallestScreenWidthDp
        Log.d(TAG, "smallestScreenWidthDp: $smallestWidthDp")
        return smallestWidthDp >= 600
    }

    /**
     * 设置屏幕方向
     * 手机：强制竖屏
     * 平板：支持横竖屏切换
     */
    private fun setRequestedOrientation() {
        if (isTabletDevice) {
            // 平板：允许横竖屏切换
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            Log.d(TAG, "平板设备，支持横竖屏切换")
        } else {
            // 手机：强制竖屏
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_USER_PORTRAIT
            Log.d(TAG, "手机设备，强制竖屏")
        }
    }

    private fun setupWindowFlags() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
            window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
            window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION)
        }
    }

    /**
     * 检查并申请文件管理权限（Android 11+）
     */
    private fun checkAndRequestManageStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                requestManageStoragePermission()
            } else {
                Log.d(TAG, "已有文件管理权限")
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            // Android 6-10 需要单独的读写权限
            val permissions = mutableListOf<String>()
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.READ_EXTERNAL_STORAGE)
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                permissions.add(android.Manifest.permission.READ_EXTERNAL_STORAGE)
            }
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                permissions.add(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
            if (permissions.isNotEmpty()) {
                storagePermissionLauncher.launch(permissions.toTypedArray())
            }
        }
    }

    /**
     * 申请全部文件管理权限
     */
    private fun requestManageStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                intent.data = Uri.parse("package:$packageName")
                manageStoragePermissionLauncher.launch(intent)
            } catch (e: Exception) {
                // 如果无法打开特定应用页面，则打开通用页面
                val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                manageStoragePermissionLauncher.launch(intent)
            }
        }
    }

    /**
     * 检查 Root 权限
     */
    private fun checkRootPermission(): Boolean {
        return try {
            val process = Runtime.getRuntime().exec("su")
            val outputStream = DataOutputStream(process.outputStream)
            outputStream.writeBytes("exit\n")
            outputStream.flush()
            process.waitFor()
            process.exitValue() == 0
        } catch (e: Exception) {
            false
        }
    }

    /**
     * 主动申请 Root 权限 - 会弹出 Magisk/SuperSU 授权窗口
     */
    private fun requestRootPermission() {
        Thread {
            try {
                // 方式1：直接执行 su 命令，系统会弹出授权窗口
                val process = Runtime.getRuntime().exec("su")
                val outputStream = DataOutputStream(process.outputStream)

                // 执行一个需要 root 权限的命令
                outputStream.writeBytes("id\n")
                outputStream.writeBytes("exit\n")
                outputStream.flush()

                // 等待命令执行完成
                val exitCode = process.waitFor()

                handler.post {
                    if (exitCode == 0) {
                        Toast.makeText(this, "Root权限已获取", Toast.LENGTH_SHORT).show()
                        Log.d(TAG, "Root权限已获取")
                    } else {
                        Toast.makeText(this, "未获取到Root权限，请在弹出的窗口中授权", Toast.LENGTH_LONG).show()
                        Log.d(TAG, "未获取到Root权限")
                    }
                }
            } catch (e: Exception) {
                handler.post {
                    Toast.makeText(this, "无法获取Root权限，请确保设备已Root", Toast.LENGTH_LONG).show()
                    Log.e(TAG, "Root权限申请失败: ${e.message}")
                }
            }
        }.start()
    }

    /**
     * 检查并自动申请 Root 权限
     */
    private fun checkAndRequestRootPermission() {
        if (!checkRootPermission()) {
            // 延迟1.5秒后主动申请
            handler.postDelayed({
                requestRootPermission()
            }, 1500)
        } else {
            Log.d(TAG, "已有Root权限")
        }
    }

    private fun checkOverlayPermissionAndStart() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.canDrawOverlays(this)) {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
                overlayPermissionLauncher.launch(intent)
                return
            }
        }
        startFloatService()
    }

    private fun startFloatService() {
        try {
            val serviceIntent = Intent(this, FloatWindowService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ContextCompat.startForegroundService(this, serviceIntent)
            } else {
                startService(serviceIntent)
            }
            Toast.makeText(this, "悬浮窗已启动", Toast.LENGTH_SHORT).show()
            Log.d(TAG, "Float service started")
        } catch (e: Exception) {
            Log.e(TAG, "Error starting service: ${e.message}", e)
            Toast.makeText(this, "启动失败: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    companion object {
        private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    }
}

@Composable
fun MainPage(
    isDark: Boolean,
    onStartFloat: () -> Unit,
    onOpenSettings: () -> Unit,
    onRequestManageStorage: () -> Unit
) {
    val context = LocalContext.current
    val hasManageStoragePermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        Environment.isExternalStorageManager()
    } else {
        ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.READ_EXTERNAL_STORAGE
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = if (isDark) {
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF1A1A2E),
                            Color(0xFF16213E)
                        )
                    )
                } else {
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFFF5F5F5),
                            Color(0xFFE8E8E8)
                        )
                    )
                }
            )
    ) {
        // 右上角设置按钮
        IconButton(
            onClick = onOpenSettings,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 25.dp, end = 5.dp)
                .size(56.dp)
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_settings),
                contentDescription = "设置",
                tint = if (isDark) Color.White else Color.Black,
                modifier = Modifier.size(36.dp)
            )
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
        ) {
            // Logo 头像区域
            Image(
                painter = painterResource(id = R.drawable.icon),
                contentDescription = "App Icon",
                modifier = Modifier
                    .size(140.dp)
                    .clip(CircleShape)
            )

            Spacer(modifier = Modifier.height(24.dp))

            // 应用名称
            Text(
                text = "泠风辅助",
                fontSize = 31.sp,
                fontWeight = FontWeight.Bold,
                color = if (isDark) Color.White else Color(0xFF1A1A2E),
                letterSpacing = 2.sp
            )

            Spacer(modifier = Modifier.height(180.dp))

            // 开启辅助按钮
            Button(
                onClick = onStartFloat,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp)
                    .height(56.dp),
                shape = RoundedCornerShape(28.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF3F51B5),
                    contentColor = Color.White
                )
            ) {
                Text(
                    text = "开启辅助",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 加入Q群按钮
            OutlinedButton(
                onClick = {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://qm.qq.com/q/zETzVnV1E4"))
                    context.startActivity(intent)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp)
                    .height(56.dp),
                shape = RoundedCornerShape(28.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = if (isDark) Color.White else Color(0xFF3F51B5)
                )
            ) {
                Text(
                    text = "加入Q群",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
        }
    }
}