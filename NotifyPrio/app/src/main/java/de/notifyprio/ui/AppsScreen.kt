package de.notifyprio.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.notifyprio.data.FilterManager
import de.notifyprio.data.RuleAction
import de.notifyprio.data.RulesState
import de.notifyprio.system.SetupHelper

@Composable
fun AppsScreen(
    rules: RulesState,
    apps: List<AppEntry>,
    onApp: (String, RuleAction) -> Unit,
    onChannel: (String, String, RuleAction) -> Unit,
) {
    val context = LocalContext.current
    var query by rememberSaveable { mutableStateOf("") }
    var onlyWithRules by rememberSaveable { mutableStateOf(false) }
    var expanded by rememberSaveable { mutableStateOf<String?>(null) }

    // Launcher-Apps + Apps, von denen wir bereits Benachrichtigungen gesehen haben
    val allApps = remember(apps, rules.knownChannels.keys) {
        val known = rules.knownChannels.keys
            .filter { pkg -> apps.none { it.pkg == pkg } }
            .map { AppEntry(it, SetupHelper.labelOf(context, it)) }
        (apps + known).sortedBy { it.label.lowercase() }
    }

    fun hasRule(pkg: String) =
        rules.appActions.containsKey(pkg) || rules.channelActions.keys.any { it.startsWith("$pkg|") }

    val shown = allApps.filter {
        (query.isBlank() || it.label.contains(query, ignoreCase = true) || it.pkg.contains(query, ignoreCase = true)) &&
            (!onlyWithRules || hasRule(it.pkg))
    }

    Column(Modifier.fillMaxSize()) {
        Text(
            "Apps & Kanäle",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 8.dp),
        )
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("App suchen") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        )
        Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            FilterChip(
                selected = onlyWithRules,
                onClick = { onlyWithRules = !onlyWithRules },
                label = { Text("Nur mit Regel") },
            )
        }
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(shown, key = { it.pkg }) { app ->
                AppRow(
                    app = app,
                    rules = rules,
                    expanded = expanded == app.pkg,
                    onToggle = { expanded = if (expanded == app.pkg) null else app.pkg },
                    onApp = onApp,
                    onChannel = onChannel,
                )
            }
        }
    }
}

@Composable
private fun AppRow(
    app: AppEntry,
    rules: RulesState,
    expanded: Boolean,
    onToggle: () -> Unit,
    onApp: (String, RuleAction) -> Unit,
    onChannel: (String, String, RuleAction) -> Unit,
) {
    val context = LocalContext.current
    val action = rules.appActions[app.pkg] ?: RuleAction.DEFAULT
    val channels = rules.knownChannels[app.pkg].orEmpty()

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().clickable(onClick = onToggle),
            ) {
                AppIcon(app.pkg)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        app.label,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        action.label("Normal") + " · " + action.description(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(if (expanded) "▲" else "▼", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            if (expanded) {
                Spacer(Modifier.height(12.dp))
                Text("Ganze App", style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.height(4.dp))
                ActionSelector(selected = action, onSelect = { onApp(app.pkg, it) })

                Spacer(Modifier.height(16.dp))
                Text("Kanäle (überschreiben die App-Regel)", style = MaterialTheme.typography.labelMedium)
                if (channels.isEmpty()) {
                    Text(
                        "Noch keine Kanäle bekannt – sie erscheinen, sobald die App eine Benachrichtigung sendet.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                channels.forEach { channel ->
                    val channelAction =
                        rules.channelActions[FilterManager.channelKey(app.pkg, channel.id)] ?: RuleAction.DEFAULT
                    Column(Modifier.padding(top = 8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                channel.name,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            TextButton(onClick = { SetupHelper.openChannelSettings(context, app.pkg, channel.id) }) {
                                Text("System", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                        ActionSelector(
                            selected = channelAction,
                            inheritLabel = "Wie App",
                            onSelect = { onChannel(app.pkg, channel.id, it) },
                        )
                    }
                }
            }
        }
    }
}
