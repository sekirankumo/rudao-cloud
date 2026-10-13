package com.eaglelan.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun UpdateDialog(vm: MainViewModel) {
    val state by vm.update.collectAsStateWithLifecycle()
    val rel = state.release ?: return
    val downloading = state.progress != null

    AlertDialog(
        onDismissRequest = { if (!downloading) vm.dismissUpdate() },
        title = { Text("发现新版本 v${rel.version}") },
        text = {
            Column {
                Text(
                    "当前 v${vm.appVersion} · 安装包 ${formatSize(rel.size)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (rel.notes.isNotBlank()) {
                    Text(
                        rel.notes,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier
                            .padding(top = 12.dp)
                            .heightIn(max = 240.dp)
                            .verticalScroll(rememberScrollState()),
                    )
                }
                when {
                    downloading -> {
                        LinearProgressIndicator(
                            progress = { state.progress ?: 0f },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 16.dp),
                        )
                        Text(
                            "正在下载 ${((state.progress ?: 0f) * 100).toInt()}%",
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                    state.needsPermission -> Text(
                        "请在打开的设置页里允许「安装未知应用」，然后返回再点一次「更新」。",
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                    state.error != null -> Text(
                        "更新失败：${state.error}",
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = vm::startUpdate, enabled = !downloading) {
                Text(if (state.error != null) "重试" else "更新")
            }
        },
        dismissButton = {
            TextButton(onClick = vm::dismissUpdate, enabled = !downloading) { Text("稍后") }
        },
    )
}
