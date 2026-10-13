package com.eaglelan.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.res.painterResource
import com.eaglelan.app.R
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@Composable
fun SetupScreen(vm: MainViewModel, canCancel: Boolean, onDone: () -> Unit) {
    val scope = rememberCoroutineScope()
    var host by rememberSaveable { mutableStateOf("") }
    var token by rememberSaveable { mutableStateOf(vm.serverToken) }
    var advanced by rememberSaveable { mutableStateOf(false) }
    var busy by rememberSaveable { mutableStateOf(false) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier
                .wrapContentWidth(Alignment.CenterHorizontally)
                .widthIn(max = 520.dp)
                .safeDrawingPadding()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Spacer(Modifier.size(32.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painterResource(R.drawable.ic_cloud),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(40.dp),
                )
                Spacer(Modifier.size(12.dp))
                Text("入到云盘", style = MaterialTheme.typography.headlineMedium)
            }
            Text(
                "在电脑的「片库整理」插件里开启手机访问，把 Token 填到这里。在家时会自动找到电脑；在外面第一次设置时，粘贴插件里的「连接码」。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = token,
                onValueChange = { token = it },
                label = { Text("Token 或连接码") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            if (advanced) {
                OutlinedTextField(
                    value = host,
                    onValueChange = { host = it },
                    label = { Text("服务器地址（可选）") },
                    placeholder = { Text("192.168.1.100:41600") },
                    supportingText = { Text("一般不用填；自动查找失败时再手动指定") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                TextButton(onClick = { advanced = true }) { Text("手动指定地址…") }
            }
            if (canCancel && vm.serverUrl.isNotEmpty()) {
                Text(
                    "当前使用：${vm.route} ${vm.serverUrl.removePrefix("http://")}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Button(
                    enabled = !busy && token.isNotBlank(),
                    onClick = {
                        busy = true
                        error = null
                        scope.launch {
                            val err = vm.connect(token, host)
                            busy = false
                            if (err == null) onDone() else error = "连接失败：$err"
                        }
                    },
                ) { Text("连接") }
                if (canCancel) {
                    TextButton(onClick = onDone, enabled = !busy) { Text("取消") }
                }
                if (busy) {
                    Spacer(Modifier.size(16.dp))
                    CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.size(8.dp))
                    Text("正在查找电脑…", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}
