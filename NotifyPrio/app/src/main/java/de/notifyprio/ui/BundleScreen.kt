package de.notifyprio.ui

import android.text.format.DateUtils
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.notifyprio.data.CapturedNotification
import de.notifyprio.data.DEFAULT_GROUP_ID
import de.notifyprio.data.DEFAULT_GROUP_NAME
import de.notifyprio.data.Outcome

@Composable
fun BundleScreen(
    captured: List<CapturedNotification>,
    groupNames: Map<String, String>,
    onOpen: (CapturedNotification) -> Unit,
    onDismiss: (CapturedNotification) -> Unit,
    onClear: (Outcome) -> Unit,
) {
    var showSuppressed by rememberSaveable { mutableStateOf(false) }
    val outcome = if (showSuppressed) Outcome.SUPPRESSED else Outcome.BUNDLED
    val shown = captured.filter { it.outcome == outcome }
    val bundledCount = captured.count { it.outcome == Outcome.BUNDLED }
    val suppressedCount = captured.size - bundledCount

    Column(Modifier.fillMaxSize()) {
        Text(
            "Bündel & Protokoll",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 8.dp),
        )
        Row(
            modifier = Modifier.padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilterChip(
                selected = !showSuppressed,
                onClick = { showSuppressed = false },
                label = { Text("Gebündelt ($bundledCount)") },
            )
            FilterChip(
                selected = showSuppressed,
                onClick = { showSuppressed = true },
                label = { Text("Unterdrückt ($suppressedCount)") },
            )
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { onClear(outcome) }, enabled = shown.isNotEmpty()) { Text("Leeren") }
        }

        if (shown.isEmpty()) {
            Text(
                if (showSuppressed) "Noch nichts unterdrückt." else "Keine gebündelten Benachrichtigungen.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp),
            )
        } else {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(shown, key = { it.key }) { item ->
                    CapturedRow(
                        item,
                        groupName = if (item.groupId == DEFAULT_GROUP_ID) DEFAULT_GROUP_NAME
                        else groupNames[item.groupId] ?: DEFAULT_GROUP_NAME,
                        onOpen = { onOpen(item) },
                        onDismiss = { onDismiss(item) },
                    )
                }
            }
        }
    }
}

@Composable
private fun CapturedRow(item: CapturedNotification, groupName: String, onOpen: () -> Unit, onDismiss: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.clickable(onClick = onOpen).padding(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            AppIcon(item.packageName, size = 36.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                val whenText = DateUtils.getRelativeTimeSpanString(item.postTime).toString()
                val channel = item.channelName?.let { " · $it" }.orEmpty()
                Text(
                    "${item.appLabel}$channel · $groupName · $whenText",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (item.title.isNotBlank()) {
                    Text(item.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 2)
                }
                if (item.text.isNotBlank()) {
                    Text(item.text, style = MaterialTheme.typography.bodyMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
                }
            }
            IconButton(onClick = onDismiss) {
                Icon(Icons.Default.Delete, contentDescription = "Verwerfen")
            }
        }
    }
}
