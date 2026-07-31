package com.rebilive.notification

import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rebilive.notification.notification.NotificationHelper

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
            val colorScheme = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                dynamicLightColorScheme(ctx)
            } else {
                lightColorScheme(primary = Color(0xFF00A1D6))
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
    val autoStart by viewModel.autoStart.collectAsState()
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
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("导出") },
                            onClick = {
                                showMenu = false
                                exportLauncher.launch("ReBLN_settings.json")
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("导入") },
                            onClick = {
                                showMenu = false
                                importLauncher.launch(arrayOf("application/json"))
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("关于") },
                            onClick = {
                                showMenu = false
                                showAboutDialog = true
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("检查更新") },
                            onClick = {
                                showMenu = false
                                viewModel.checkForUpdates()
                            }
                        )
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
                        containerColor = if (isServiceRunning) Color(0xFFFF9800) else Color(0xFF00A1D6)
                    ),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(if (isServiceRunning) "停止检测" else "开始检测")
                }
                OutlinedButton(
                    onClick = { viewModel.saveSettings() },
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("保存设置")
                }
                OutlinedButton(
                    onClick = { viewModel.refreshStatus() },
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("刷新状态")
                }
            }

            Spacer(Modifier.height(2.dp))

            Row(
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Switch(
                        checked = notifyEnabled,
                        onCheckedChange = { viewModel.setNotifyEnabled(it) },
                        modifier = Modifier.scale(0.8f)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("弹窗提醒", style = MaterialTheme.typography.bodySmall)
                }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Switch(
                        checked = autoJumpEnabled,
                        onCheckedChange = { viewModel.setAutoJumpEnabled(it) },
                        modifier = Modifier.scale(0.8f)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("自动跳转", style = MaterialTheme.typography.bodySmall)
                }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Switch(
                        checked = autoStart,
                        onCheckedChange = { viewModel.setAutoStart(it) },
                        modifier = Modifier.scale(0.8f)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("开机自启", style = MaterialTheme.typography.bodySmall)
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
                    singleLine = true
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

            Spacer(Modifier.height(12.dp))

            LazyColumn(modifier = Modifier.weight(1f)) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFFE0E0E0))
                            .padding(8.dp)
                    ) {
                        Text("主播", Modifier.weight(1f), fontWeight = FontWeight.Bold)
                        Text("房间号", Modifier.weight(1f), fontWeight = FontWeight.Bold)
                        Text("状态", Modifier.weight(1f), fontWeight = FontWeight.Bold)
                    }
                }
                items(roomStatusList, key = { it.roomId }) { status ->
                    Row(modifier = Modifier.padding(8.dp)) {
                        Text(status.uname, Modifier.weight(1f))
                        Text(status.roomId, Modifier.weight(1f))
                        Text(status.status, Modifier.weight(1f))
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
                            text = "版本 v1.1\n" +
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
