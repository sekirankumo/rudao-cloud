package com.eaglelan.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eaglelan.app.data.LoudMode

/** Everything that used to live in the ⋮ menu: server, loudness, folders, updates. */
@Composable
fun SettingsScreen(vm: MainViewModel, onBack: () -> Unit, onChangeServer: () -> Unit) {
    PlatformBackHandler(onBack)
    val conn by vm.conn.collectAsStateWithLifecycle()
    val connDetail by vm.connDetail.collectAsStateWithLifecycle()
    val libraryName by vm.libraryName.collectAsStateWithLifecycle()
    val loudMode by vm.loudMode.collectAsStateWithLifecycle()
    val previewSeconds by vm.previewSeconds.collectAsStateWithLifecycle()
    val showFolders by vm.showFolders.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize().background(AppColors.Background)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .background(AppColors.Surface1)
                .statusBarsPadding()
                .height(56.dp)
                .padding(horizontal = 4.dp),
        ) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") }
            Text("设置", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = 4.dp))
        }
        Column(
            verticalArrangement = Arrangement.spacedBy(20.dp),
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .wrapContentWidth(Alignment.CenterHorizontally)
                .widthIn(max = 640.dp)
                .navigationBarsPadding()
                .padding(horizontal = 12.dp, vertical = 16.dp),
        ) {
            Section("服务器") {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(16.dp)) {
                    Box(
                        Modifier.size(10.dp).background(
                            when (conn) {
                                Conn.ONLINE -> AppColors.Online
                                Conn.OFFLINE -> AppColors.Offline
                                Conn.CHECKING -> AppColors.Checking
                            },
                            CircleShape,
                        )
                    )
                    Column(Modifier.padding(start = 12.dp)) {
                        Text(libraryName.ifEmpty { "Eagle 素材库" }, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            when (conn) {
                                Conn.ONLINE -> "已连接 · " + vm.serverUrl.substringAfter("://")
                                Conn.OFFLINE -> "连不上 · $connDetail"
                                Conn.CHECKING -> "正在检查…"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = AppColors.Text2,
                        )
                    }
                }
                HorizontalDivider(Modifier.padding(start = 16.dp), color = AppColors.Surface3)
                TextButton(
                    onClick = onChangeServer,
                    shape = AppShapes.Medium,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                ) { Text("更换服务器…", modifier = Modifier.fillMaxWidth()) }
            }

            Section("播放") {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column {
                        Text("响度统一", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "太响的压低；“标准”还会把太轻的最多拉高 8 dB",
                            style = MaterialTheme.typography.bodySmall,
                            color = AppColors.Text2,
                        )
                    }
                    Segmented(
                        options = listOf(LoudMode.STANDARD to "标准", LoudMode.DOWN to "只压不抬", LoudMode.OFF to "关"),
                        selected = loudMode,
                        onSelect = vm::setLoudMode,
                    )
                }
                HorizontalDivider(Modifier.padding(start = 16.dp), color = AppColors.Surface3)
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("随机速览每段", style = MaterialTheme.typography.bodyLarge)
                    Segmented(
                        options = listOf(5, 10, 15, 30).map { it to "${it}秒" },
                        selected = previewSeconds,
                        onSelect = vm::setPreviewSeconds,
                    )
                }
            }

            Section("资源库") {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clickable(role = Role.Switch) { vm.setShowFolders(!showFolders) }
                        .padding(16.dp),
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("显示 Eagle 文件夹", style = MaterialTheme.typography.bodyLarge)
                        Text("侧栏里加上文件夹树、未分类和回收站", style = MaterialTheme.typography.bodySmall, color = AppColors.Text2)
                    }
                    Switch(
                        checked = showFolders,
                        onCheckedChange = null,
                        colors = SwitchDefaults.colors(
                            checkedTrackColor = AppColors.Brand,
                            checkedThumbColor = Color.White,
                            uncheckedTrackColor = AppColors.Surface3,
                            uncheckedThumbColor = AppColors.Text3,
                            uncheckedBorderColor = AppColors.Text3,
                        ),
                    )
                }
            }

            Section("关于") {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(16.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text("检查更新", style = MaterialTheme.typography.bodyLarge)
                        Text("当前 v${vm.appVersion}", style = MaterialTheme.typography.bodySmall, color = AppColors.Text2)
                    }
                    OutlinedButton(onClick = { vm.checkForUpdate(manual = true) }) { Text("检查") }
                }
            }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.labelLarge,
            color = AppColors.Text2,
            modifier = Modifier.padding(horizontal = 8.dp),
        )
        Column(Modifier.fillMaxWidth().clip(AppShapes.Medium).background(AppColors.Surface1), content = content)
    }
}

/** A pill-shaped single-choice row (the prototype's 响度 / 每段 selectors). */
@Composable
private fun <T> Segmented(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier
            .fillMaxWidth()
            .background(AppColors.Background, AppShapes.Full)
            .border(1.dp, AppColors.Surface3, AppShapes.Full)
            .padding(4.dp),
    ) {
        options.forEach { (value, label) ->
            val on = value == selected
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .weight(1f)
                    .height(36.dp)
                    .clip(AppShapes.Full)
                    .background(if (on) AppColors.Selected else Color.Transparent)
                    .clickable(role = Role.RadioButton) { onSelect(value) },
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (on) AppColors.OnSelected else AppColors.Text2,
                )
            }
        }
    }
}
