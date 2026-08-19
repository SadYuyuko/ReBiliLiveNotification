package com.rebilive.notification

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.background
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rebilive.notification.data.SettingsRepository
import com.rebilive.notification.notification.NotificationHelper

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (SettingsRepository(this).isHideToBackground() &&
            (intent.flags and Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS) == 0
        ) {
            finish()
            startActivity(
                Intent(this, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
                }
            )
            return
        }
        enableEdgeToEdge()
        NotificationHelper(this).createChannels()

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (viewModel.hideToBackground.value) {
                    moveTaskToBack(true)
                } else {
                    finish()
                }
            }
        })

        setContent {
            val ctx = LocalContext.current
            val darkTheme = isSystemInDarkTheme()
            val colorScheme = when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && darkTheme -> dynamicDarkColorScheme(ctx)
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> dynamicLightColorScheme(ctx)
                darkTheme -> darkColorScheme(
                    primary = Color(0xFF00A1D6),
                    secondary = Color(0xFF4FC3F7),
                    tertiary = Color(0xFFFF9800)
                )
                else -> lightColorScheme(primary = Color(0xFF00A1D6))
            }
            MaterialTheme(colorScheme = colorScheme) {
                MainScreen(viewModel)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: MainViewModel) {
    val context = LocalContext.current
    val roomIds by viewModel.roomIds.collectAsState()
    val interval by viewModel.interval.collectAsState()
    val notifyEnabled by viewModel.notifyEnabled.collectAsState()
    val autoJumpEnabled by viewModel.autoJumpEnabled.collectAsState()
    val apiUrl by viewModel.apiUrl.collectAsState()
    val hideToBackground by viewModel.hideToBackground.collectAsState()
    val roomStatusList by viewModel.roomStatusList.collectAsState()
    val isServiceRunning by viewModel.isServiceRunning.collectAsState()
    var showAboutDialog by remember { mutableStateOf(false) }
    val updateResult by viewModel.updateCheckResult.collectAsState()
    val importResult by viewModel.importResult.collectAsState()

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            try {
                val json = viewModel.exportSettings()
                context.contentResolver.openOutputStream(uri)?.use { os ->
                    os.write(json.toByteArray(Charsets.UTF_8))
                }
                Toast.makeText(context, "导出成功", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, "导出失败: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            try {
                val json = context.contentResolver.openInputStream(uri)?.use { ins ->
                    ins.readBytes().toString(Charsets.UTF_8)
                }
                if (json != null) {
                    viewModel.importSettings(json)
                }
            } catch (e: Exception) {
                Toast.makeText(context, "导入失败: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    LaunchedEffect(importResult) {
        if (importResult != null) {
            Toast.makeText(context, importResult, Toast.LENGTH_SHORT).show()
            viewModel.clearImportResult()
        }
    }

    LaunchedEffect(Unit) {
        viewModel.refreshStatus()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("B站开播提醒") },
                actions = {
                    var showMenu by remember { mutableStateOf(false) }
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Default.MoreVert, "更多")
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false },
                        modifier = Modifier.width(84.dp)
                    ) {
                        MenuItemText("导出") {
                            showMenu = false
                            exportLauncher.launch("ReBLN_settings.json")
                        }
                        MenuItemText("导入") {
                            showMenu = false
                            importLauncher.launch(arrayOf("application/json"))
                        }
                        MenuItemText("关于") {
                            showMenu = false
                            showAboutDialog = true
                        }
                        MenuItemText("更新") {
                            showMenu = false
                            viewModel.checkForUpdates()
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { viewModel.toggleService() },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isServiceRunning) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
                        contentColor = if (isServiceRunning) MaterialTheme.colorScheme.onTertiary else MaterialTheme.colorScheme.onPrimary
                    ),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(if (isServiceRunning) "停止检测" else "开始检测")
                }
                OutlinedButton(
                    onClick = { viewModel.saveSettings() },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("保存设置")
                }
                OutlinedButton(
                    onClick = { viewModel.refreshStatus() },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("刷新状态")
                }
            }

            Spacer(Modifier.height(2.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.weight(1f)
                ) {
                    Switch(
                        checked = notifyEnabled,
                        onCheckedChange = { viewModel.setNotifyEnabled(it) }
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("弹窗提醒", style = MaterialTheme.typography.bodySmall)
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.weight(1f)
                ) {
                    Switch(
                        checked = autoJumpEnabled,
                        onCheckedChange = { viewModel.setAutoJumpEnabled(it) }
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("自动跳转", style = MaterialTheme.typography.bodySmall)
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.weight(1f)
                ) {
                    Switch(
                        checked = hideToBackground,
                        onCheckedChange = { enabled ->
                            viewModel.setHideToBackground(enabled)
                            val activity = context as? Activity
                            if (activity != null) {
                                activity.finish()
                                val intent = Intent(activity, MainActivity::class.java).apply {
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    if (enabled) {
                                        addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
                                    }
                                }
                                activity.startActivity(intent)
                            }
                        }
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("隐藏后台", style = MaterialTheme.typography.bodySmall)
                }
            }

            Spacer(Modifier.height(2.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = apiUrl,
                    onValueChange = { viewModel.apiUrl.value = it },
                    label = { Text("API") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    trailingIcon = {
                        IconButton(onClick = { viewModel.restoreDefaultApi() }) {
                            Icon(Icons.Filled.Refresh, contentDescription = "重置")
                        }
                    }
                )
                Spacer(Modifier.width(8.dp))
                OutlinedTextField(
                    value = interval,
                    onValueChange = { viewModel.updateInterval(it) },
                    label = { Text("间隔(s)") },
                    modifier = Modifier.width(90.dp),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
            }

            Spacer(Modifier.height(8.dp))

            OutlinedTextField(
                value = roomIds,
                onValueChange = { viewModel.roomIds.value = it },
                label = { Text("房间号") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(Modifier.height(8.dp))

            PermissionGuideCard()

            Spacer(Modifier.height(8.dp))

            LazyColumn(modifier = Modifier.weight(1f)) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(8.dp)
                    ) {
                        Text("主播", Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
                        Text("房间号", Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
                        Text("状态", Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
                    }
                }
                items(roomStatusList, key = { it.roomId }) { status ->
                    Row(modifier = Modifier.padding(8.dp)) {
                        Text(status.uname, Modifier.weight(1f))
                        Text(status.roomId, Modifier.weight(1f))
                        Text(
                            text = status.status,
                            modifier = Modifier.weight(1f),
                            color = when (status.status) {
                                "直播中" -> MaterialTheme.colorScheme.primary
                                "获取失败", "网络错误" -> MaterialTheme.colorScheme.error
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            fontWeight = FontWeight.Medium
                        )
                    }
                    HorizontalDivider()
                }
            }
        }
    }

    if (showAboutDialog) {
        AlertDialog(
            onDismissRequest = { showAboutDialog = false },
            title = { Text("关于") },
            text = {
                Column(Modifier.widthIn(max = 400.dp)) {
                    SelectionContainer {
                        Text(
                            text = "版本 v1.3\n" +
                                    "原作者 @yunhuanyx\n" +
                                    "原项目: github.com/yunhuanyx/biliLiveNotification\n" +
                                    "Re版: github.com/SadYuyuko/ReBiliLiveNotification"
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAboutDialog = false }) { Text("关闭") }
            }
        )
    }

    if (updateResult != null) {
        AlertDialog(
            onDismissRequest = { viewModel.clearUpdateResult() },
            title = { Text(if (updateResult!!.success) "检查更新" else "错误") },
            text = { Text(updateResult!!.message) },
            confirmButton = {
                TextButton(onClick = {
                    if (updateResult!!.url != null) {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(updateResult!!.url)))
                    }
                    viewModel.clearUpdateResult()
                }) { Text(if (updateResult!!.url != null) "确定" else "关闭") }
            }
        )
    }
}

@Composable
private fun MenuItemText(text: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
fun PermissionGuideCard(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var resumeTick by remember { mutableStateOf(0) }
    LifecycleResumeEffect(Unit) {
        resumeTick++
        onPauseOrDispose { }
    }
    resumeTick

    var notifyGranted by remember { mutableStateOf(checkNotifyPermission(context)) }
    val notifyLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> notifyGranted = granted }
    val canOverlay = Settings.canDrawOverlays(context)
    val canFullScreen = checkFullScreenPermission(context)

    if (notifyGranted && canOverlay && canFullScreen) return

    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text("后台弹窗/自动跳转需要以下权限：", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            if (!notifyGranted) {
                PermissionRow("通知权限", "用于弹出开播提醒") {
                    notifyLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
            if (!canOverlay) {
                PermissionRow("悬浮窗权限", "用于后台自动跳转") {
                    openOverlaySettings(context)
                }
            }
            if (!canFullScreen) {
                PermissionRow("全屏通知权限", "用于熄屏时弹出提醒") {
                    openFullScreenSettings(context)
                }
            }
        }
    }
}

@Composable
private fun PermissionRow(title: String, subtitle: String, onGrant: () -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 14.sp)
            Text(subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        TextButton(onClick = onGrant) { Text("去授权") }
    }
}

private fun checkNotifyPermission(context: Context): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
        PackageManager.PERMISSION_GRANTED

private fun checkFullScreenPermission(context: Context): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.USE_FULL_SCREEN_INTENT) ==
        PackageManager.PERMISSION_GRANTED

private fun openOverlaySettings(context: Context) {
    val intent = try {
        Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${context.packageName}")
        )
    } catch (_: Exception) {
        Intent(Settings.ACTION_SETTINGS)
    }
    context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

private fun openFullScreenSettings(context: Context) {
    val intent = try {
        Intent(
            Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,
            Uri.parse("package:${context.packageName}")
        )
    } catch (_: Exception) {
        Intent(Settings.ACTION_SETTINGS)
    }
    context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}
