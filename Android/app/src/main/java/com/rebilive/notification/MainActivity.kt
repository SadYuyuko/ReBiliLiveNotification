package com.rebilive.notification

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent
import android.content.Intent
import android.net.Uri
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rebilive.notification.notification.NotificationHelper

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        NotificationHelper(this).createChannels()
        setContent {
            val ctx = LocalContext.current
            val colorScheme = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                dynamicLightColorScheme(ctx)
            } else {
                lightColorScheme(primary = Color(0xFF00A1D6))
            }
            MaterialTheme(colorScheme = colorScheme) {
                MainScreen()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: MainViewModel = viewModel()) {
    val context = LocalContext.current
    val roomIds by viewModel.roomIds.collectAsState()
    val interval by viewModel.interval.collectAsState()
    val notifyEnabled by viewModel.notifyEnabled.collectAsState()
    val autoJumpEnabled by viewModel.autoJumpEnabled.collectAsState()
    val roomStatusList by viewModel.roomStatusList.collectAsState()
    val isServiceRunning by viewModel.isServiceRunning.collectAsState()
    var showAboutDialog by remember { mutableStateOf(false) }
    val updateResult by viewModel.updateCheckResult.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Re：B站开播提醒") },
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
                .padding(16.dp)
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

            Spacer(Modifier.height(8.dp))

            OutlinedTextField(
                value = roomIds,
                onValueChange = { viewModel.roomIds.value = it },
                label = { Text("房间号（逗号隔开）") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(Modifier.height(8.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = interval,
                    onValueChange = { viewModel.updateInterval(it) },
                    label = { Text("间隔") },
                    modifier = Modifier.width(72.dp),
                    singleLine = true
                )
                Spacer(Modifier.width(8.dp))
                Checkbox(checked = notifyEnabled, onCheckedChange = { viewModel.setNotifyEnabled(it) })
                Text("弹窗提醒")
                Spacer(Modifier.width(4.dp))
                Checkbox(checked = autoJumpEnabled, onCheckedChange = { viewModel.setAutoJumpEnabled(it) })
                Text("自动跳转")
            }

            Spacer(Modifier.height(16.dp))

            Text("直播间状态", fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))

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
                            text = "版本 v1.0\n" +
                                    "原作者 @yunhuanyx\n" +
                                    "原项目: github.com/yunhuanyx/biliLiveNotification\n" +
                                    "Re版: github.com/SadYuyuko/biliLiveNotification"
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
