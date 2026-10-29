package de.notifyprio.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import de.notifyprio.service.KeepAliveService
import de.notifyprio.system.ListenerGuard

class MainActivity : ComponentActivity() {

    private var requestedTab by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge() // Android 16 / targetSdk 36 erzwingt Edge-to-Edge ohnehin

        requestedTab = intent?.getStringExtra(EXTRA_TAB)
        ListenerGuard.ensureEnabled(this)
        KeepAliveService.start(this) // App ist sichtbar -> Foreground-Start erlaubt

        setContent {
            NotifyPrioTheme {
                AppRoot(
                    requestedTab = requestedTab,
                    onTabConsumed = { requestedTab = null },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        requestedTab = intent.getStringExtra(EXTRA_TAB)
    }

    companion object {
        const val EXTRA_TAB = "tab"
    }
}
