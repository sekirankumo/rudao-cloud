package com.eaglelan.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eaglelan.app.data.tagValueOf

private const val COLLAPSED_CHIPS = 12

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TagFilterSheet(vm: MainViewModel, onDismiss: () -> Unit) {
    val groups by vm.tagGroups.collectAsStateWithLifecycle()
    val selected by vm.selectedTags.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    var expanded by rememberSaveable { mutableStateOf(setOf<String>()) }

    val visible = remember(groups, query) {
        val q = query.trim()
        if (q.isEmpty()) groups
        else groups.mapNotNull { g ->
            val hit = if (g.name.contains(q, true)) g.tags else g.tags.filter { it.tag.contains(q, true) }
            if (hit.isEmpty()) null else g.copy(tags = hit)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 20.dp),
        ) {
            Text("按标签筛选", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.weight(1f))
            if (selected.isNotEmpty()) {
                TextButton(onClick = { vm.selectedTags.value = emptySet() }) {
                    Text("清除 (${selected.size})")
                }
            }
        }
        Text(
            "同组内任选其一，不同组需同时满足",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp),
        )
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("搜索标签") },
            leadingIcon = { Icon(Icons.Default.Search, null) },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
        )
        LazyColumn(
            modifier = Modifier.fillMaxHeight(0.85f),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 32.dp),
        ) {
            items(visible, key = { it.name }) { group ->
                val showAll = query.isNotBlank() || group.name in expanded
                val chips = if (showAll) group.tags else group.tags.take(COLLAPSED_CHIPS)
                val title = group.name
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(start = 20.dp, end = 8.dp, top = 12.dp),
                ) {
                    Text(title, style = MaterialTheme.typography.titleSmall)
                    Text(
                        "  ${group.tags.size}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.weight(1f))
                    if (!showAll && group.tags.size > COLLAPSED_CHIPS) {
                        TextButton(onClick = { expanded = expanded + group.name }) {
                            Text("全部 ${group.tags.size}")
                        }
                    } else if (query.isBlank() && group.name in expanded) {
                        TextButton(onClick = { expanded = expanded - group.name }) { Text("收起") }
                    }
                }
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(horizontal = 20.dp),
                ) {
                    chips.forEach { tc ->
                        val on = tc.tag in selected
                        FilterChip(
                            selected = on,
                            onClick = { vm.toggleTag(tc.tag) },
                            label = { Text("${tagValueOf(tc.tag)}  ${tc.n}") },
                            leadingIcon = if (on) {
                                { Icon(Icons.Default.Check, null, Modifier.padding(0.dp)) }
                            } else null,
                            colors = FilterChipDefaults.filterChipColors(),
                        )
                    }
                }
            }
        }
    }
}
