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
import com.eaglelan.app.resources.Res
import com.eaglelan.app.resources.ic_cloud
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource

@Composable
fun SetupScreen(vm: MainViewModel, canCancel: Boolean, onDone: () -> Unit) {
    val scope = rememberCoroutineScope()
    var host by rememberSaveable { mutableStateOf(vm.serverUrl.removePrefix("http://")) }
    var token by rememberSaveable { mutableStateOf(vm.serverToken) }
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
                    painterResource(Res.drawable.ic_cloud),
                    contentDescription = null,
                    tint = AppColors.Brand,
                    modifier = Modifier.size(40.dp),
                )
                Spacer(Modifier.size(12.dp))
                Text("入到云盘", style = MaterialTheme.typography.headlineMedium)
            }
            Text(
                "连接电脑上的 Eagle 库：在电脑上开启手机访问服务，把显示的地址和 Token 填到这里。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = host,
                onValueChange = { host = it },
                label = { Text("服务器地址") },
                placeholder = { Text("192.168.1.100:41600") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = token,
                onValueChange = { token = it },
                label = { Text("Token") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Button(
                    colors = brandButtonColors(),
                    enabled = !busy && host.isNotBlank() && token.isNotBlank(),
                    onClick = {
                        busy = true
                        error = null
                        scope.launch {
                            val err = vm.connect(host, token)
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
                }
            }
        }
    }
}
