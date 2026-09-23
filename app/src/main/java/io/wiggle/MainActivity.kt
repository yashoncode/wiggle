package io.wiggle

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.glance.appwidget.updateAll
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import io.wiggle.alarm.Alarms
import io.wiggle.ui.WiggleRoot
import io.wiggle.widget.WiggleWidget
import kotlinx.coroutines.launch

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

    /**
     * Some phones will not start the app for a widget broadcast at all, which leaves the widget on
     * its placeholder however long you wait. Opening the app is the one moment we are certainly
     * running, so redraw it here.
     */
    override fun onStart() {
        super.onStart()
        lifecycleScope.launch {
            runCatching { WiggleWidget().updateAll(this@MainActivity) }
                // Swallowed, because a widget is never worth a crash, but not silently: a widget
                // that stays on its placeholder leaves no other trace of why.
                .onFailure { Log.w("WiggleWidget", "Could not redraw the widget", it) }
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
