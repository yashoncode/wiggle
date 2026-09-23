package io.wiggle

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import dagger.hilt.android.AndroidEntryPoint
import io.wiggle.alarm.Alarms
import io.wiggle.ui.WiggleRoot

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    /** Which screen a notification tap asked for, consumed once the UI has acted on it. */
    private var openTarget by mutableStateOf<String?>(null)

    private val requestNotifications =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        openTarget = intent?.getStringExtra(Alarms.EXTRA_OPEN)
        askForNotificationsOnce()
        setContent {
            WiggleRoot(
                openTarget = openTarget,
                onOpenTargetHandled = { openTarget = null },
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        openTarget = intent.getStringExtra(Alarms.EXTRA_OPEN)
    }

    /**
     * Asked for on first launch rather than behind a toggle: the reminders are off by default, so
     * the prompt is the only thing standing between switching one on and it actually arriving.
     */
    private fun askForNotificationsOnce() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted) requestNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}
