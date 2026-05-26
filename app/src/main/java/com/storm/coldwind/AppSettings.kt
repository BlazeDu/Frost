package com.storm.coldwind

import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.storm.coldwind.ui.theme.AppTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader

class AppSettings : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            AppTheme {
                val isDark = ThemeManager.isDarkTheme(this)
                var isExpanded by remember { mutableStateOf(false) }
                var selectedMode by remember { mutableStateOf(ThemeManager.getThemeMode(this)) }
                var hasRootPermission by remember { mutableStateOf(checkRootPermission()) }
                var hasFilePermission by remember { mutableStateOf(checkFilePermission()) }

                val view = LocalView.current
                SideEffect {
                    val window = (view.context as androidx.activity.ComponentActivity).window
                    WindowCompat.setDecorFitsSystemWindows(window, false)
                    val insetsController = WindowCompat.getInsetsController(window, view)
                    insetsController.isAppearanceLightStatusBars = !isDark
                    insetsController.isAppearanceLightNavigationBars = !isDark
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        window.statusBarColor = android.graphics.Color.TRANSPARENT
                        window.navigationBarColor = android.graphics.Color.TRANSPARENT
                    }
                }

                SettingsPageContent(
                    isDark = isDark,
                    currentMode = selectedMode,
                    isExpanded = isExpanded,
                    hasRootPermission = hasRootPermission,
                    hasFilePermission = hasFilePermission,
                    onHeaderClick = { isExpanded = !isExpanded },
                    onModeChange = { newMode ->
                        selectedMode = newMode
                        isExpanded = false
                    },
                    onConfirmModeChange = { newMode ->
                        ThemeManager.saveThemeMode(this, newMode)
                        ThemeManager.restartApp(this)
                    },
                    onBack = {
                        finish()
                    }
                )
            }
        }
    }

    /**
     * 检查 Root 权限
     */
    private fun checkRootPermission(): Boolean {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "echo test"))
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val output = reader.readLine()
            process.waitFor()
            reader.close()
            output != null && process.exitValue() == 0
        } catch (e: Exception) {
            false
        }
    }

    /**
     * 检查文件管理权限
     */
    private fun checkFilePermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            checkSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }
}

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun SettingsPageContent(
    isDark: Boolean,
    currentMode: Int,
    isExpanded: Boolean,
    hasRootPermission: Boolean,
    hasFilePermission: Boolean,
    onHeaderClick: () -> Unit,
    onModeChange: (Int) -> Unit,
    onConfirmModeChange: (Int) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // 获取应用版本号
    val versionName = remember {
        try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            packageInfo.versionName ?: "获取失败"
        } catch (e: PackageManager.NameNotFoundException) {
            "1.0.0"
        }
    }

    // 箭头颜色根据主题变化
    val arrowColor = if (isDark) Color.White else Color(0xFF333333)

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
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // 顶部栏
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 48.dp, start = 16.dp, end = 16.dp, bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 返回按钮
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_back),
                        contentDescription = "返回",
                        tint = if (isDark) Color.White else Color.Black,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Text(
                    text = "设置",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isDark) Color.White else Color(0xFF1A1A2E)
                )
            }

            // 设置列表
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp)
            ) {
                // ========== 外观分组 ==========
                Text(
                    text = "外观",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (isDark) Color.White else Color(0xFF1A1A2E),
                    modifier = Modifier.padding(start = 8.dp, bottom = 8.dp)
                )

                // 主题设置选项 - 可展开
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            color = if (isDark) Color(0xFF2A2A2A) else Color.White
                        )
                ) {
                    // 主题标题行
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onHeaderClick() }
                            .padding(horizontal = 16.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "主题",
                            fontSize = 16.sp,
                            color = if (isDark) Color.White else Color(0xFF1A1A2E)
                        )

                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = ThemeManager.getThemeDisplayName(currentMode),
                                fontSize = 14.sp,
                                color = if (isDark) Color(0xFFAAAAAA) else Color(0xFF999999)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Icon(
                                painter = painterResource(id = R.drawable.ic_arrow_down),
                                contentDescription = if (isExpanded) "收起" else "展开",
                                tint = arrowColor,
                                modifier = Modifier
                                    .size(20.dp)
                                    .rotate(if (isExpanded) 180f else 0f)
                            )
                        }
                    }

                    // 展开的选项列表 - 带动画
                    AnimatedVisibility(
                        visible = isExpanded,
                        enter = expandVertically(
                            animationSpec = tween(durationMillis = 250)
                        ) + fadeIn(
                            animationSpec = tween(durationMillis = 200)
                        ),
                        exit = shrinkVertically(
                            animationSpec = tween(durationMillis = 200)
                        ) + fadeOut(
                            animationSpec = tween(durationMillis = 150)
                        )
                    ) {
                        Column {
                            // 分隔线
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(1.dp)
                                    .background(
                                        if (isDark) Color(0xFF444444) else Color(0xFFEEEEEE)
                                    )
                            )

                            // 跟随系统选项
                            ThemeOptionItem(
                                title = "跟随系统",
                                isSelected = currentMode == 0,
                                onClick = {
                                    onModeChange(0)
                                    coroutineScope.launch {
                                        delay(250)
                                        onConfirmModeChange(0)
                                    }
                                },
                                isDark = isDark
                            )

                            // 分隔线
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(1.dp)
                                    .background(
                                        if (isDark) Color(0xFF444444) else Color(0xFFEEEEEE)
                                    )
                            )

                            // 深色模式选项
                            ThemeOptionItem(
                                title = "深色模式",
                                isSelected = currentMode == 1,
                                onClick = {
                                    onModeChange(1)
                                    coroutineScope.launch {
                                        delay(250)
                                        onConfirmModeChange(1)
                                    }
                                },
                                isDark = isDark
                            )

                            // 分隔线
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(1.dp)
                                    .background(
                                        if (isDark) Color(0xFF444444) else Color(0xFFEEEEEE)
                                    )
                            )

                            // 浅色模式选项
                            ThemeOptionItem(
                                title = "浅色模式",
                                isSelected = currentMode == 2,
                                onClick = {
                                    onModeChange(2)
                                    coroutineScope.launch {
                                        delay(250)
                                        onConfirmModeChange(2)
                                    }
                                },
                                isDark = isDark
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // ========== 权限分组 ==========
                Text(
                    text = "权限",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (isDark) Color.White else Color(0xFF1A1A2E),
                    modifier = Modifier.padding(start = 8.dp, bottom = 8.dp)
                )

                // 权限状态显示
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            color = if (isDark) Color(0xFF2A2A2A) else Color.White
                        )
                ) {
                    // Root权限状态
                    PermissionStatusItem(
                        title = "Root权限",
                        isGranted = hasRootPermission,
                        isDark = isDark
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(
                                if (isDark) Color(0xFF444444) else Color(0xFFEEEEEE)
                            )
                    )

                    // 文件管理权限状态
                    PermissionStatusItem(
                        title = "文件管理权限",
                        isGranted = hasFilePermission,
                        isDark = isDark
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // ========== 关于分组 ==========
                Text(
                    text = "关于",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (isDark) Color.White else Color(0xFF1A1A2E),
                    modifier = Modifier.padding(start = 8.dp, bottom = 8.dp)
                )

                // 关于分组内容
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            color = if (isDark) Color(0xFF2A2A2A) else Color.White,
                            shape = RoundedCornerShape(12.dp)
                        )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "版本号",
                            fontSize = 16.sp,
                            color = if (isDark) Color.White else Color(0xFF1A1A2E)
                        )
                        Text(
                            text = versionName,
                            fontSize = 14.sp,
                            color = if (isDark) Color(0xFFAAAAAA) else Color(0xFF999999)
                        )
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(
                                if (isDark) Color(0xFF444444) else Color(0xFFEEEEEE)
                            )
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "开发者",
                            fontSize = 16.sp,
                            color = if (isDark) Color.White else Color(0xFF1A1A2E)
                        )
                        Text(
                            text = "WGJ",
                            fontSize = 14.sp,
                            color = if (isDark) Color(0xFFAAAAAA) else Color(0xFF999999)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun PermissionStatusItem(
    title: String,
    isGranted: Boolean,
    isDark: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = title,
            fontSize = 15.sp,
            color = if (isDark) Color.White else Color(0xFF1A1A2E)
        )

        Text(
            text = if (isGranted) "已授予" else "未授予",
            fontSize = 13.sp,
            color = if (isGranted) Color(0xFF4CAF50) else Color(0xFFF44336)
        )
    }
}

@Composable
fun ThemeOptionItem(
    title: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    isDark: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = title,
            fontSize = 15.sp,
            color = if (isDark) Color.White else Color(0xFF1A1A2E)
        )

        if (isSelected) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color(0xFF3F51B5))
            )
        }
    }
}