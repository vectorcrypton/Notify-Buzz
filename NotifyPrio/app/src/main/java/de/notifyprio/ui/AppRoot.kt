package de.notifyprio.ui

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

private data class TabItem(val label: String, val icon: ImageVector)

private val tabs = listOf(
    TabItem("Apps", Icons.Default.Home),
    TabItem("Fokus", Icons.Default.Notifications),
    TabItem("Bündel", Icons.Default.Email),
    TabItem("Setup", Icons.Default.Settings),
)

@Composable
fun AppRoot(
    requestedTab: String?,
    onTabConsumed: () -> Unit,
    vm: MainViewModel = viewModel(),
) {
    var tab by rememberSaveable { mutableIntStateOf(0) }

    LaunchedEffect(requestedTab) {
        if (requestedTab == "bundle") tab = 2
        if (requestedTab != null) onTabConsumed()
    }

    // Status beim Zurückkehren aus den Systemeinstellungen neu einlesen
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) vm.refreshStatus()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { vm.refreshStatus() }

    val rules by vm.rules.collectAsStateWithLifecycle()
    val apps by vm.apps.collectAsStateWithLifecycle()
    val items by vm.items.collectAsStateWithLifecycle()
    val status by vm.status.collectAsStateWithLifecycle()

    Scaffold(
        bottomBar = {
            NavigationBar {
                tabs.forEachIndexed { index, item ->
                    NavigationBarItem(
                        selected = tab == index,
                        onClick = { tab = index },
                        icon = { Icon(item.icon, contentDescription = null) },
                        label = { Text(item.label) },
                    )
                }
            }
        },
    ) { inner ->
        Box(Modifier.padding(inner)) {
            when (tab) {
                0 -> AppsScreen(
                    rules = rules,
                    apps = apps,
                    onApp = vm::setApp,
                    onChannel = vm::setChannel,
                )
                1 -> FocusScreen(
                    rules = rules,
                    status = status,
                    onMode = vm::setFocus,
                    onMirrorDnd = vm::setMirrorDnd,
                )
                2 -> BundleScreen(
                    captured = items,
                    onOpen = vm::openItem,
                    onDismiss = vm::dismissItem,
                    onClear = vm::clear,
                )
                else -> SetupScreen(
                    status = status,
                    onRequestPostNotifications = {
                        notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    },
                    onToggleService = vm::toggleService,
                )
            }
        }
    }
}
