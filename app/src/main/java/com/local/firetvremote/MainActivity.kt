package com.local.firetvremote

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.local.firetvremote.ui.FireRemoteApp
import com.local.firetvremote.ui.FireRemoteTheme
import com.local.firetvremote.ui.isDark

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val model: RemoteViewModel = viewModel()
            val state by model.state.collectAsState()
            val dark = state.themeMode.isDark()
            // Match the status and navigation bar icons to the app's theme,
            // which can differ from the phone's own setting.
            DisposableEffect(dark) {
                val bars = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark }
                enableEdgeToEdge(statusBarStyle = bars, navigationBarStyle = bars)
                onDispose {}
            }
            FireRemoteTheme(dark = dark) {
                FireRemoteApp(
                    state = state,
                    onScan = model::scan,
                    onManualAddress = model::onManualAddress,
                    onPairDiscovered = { model.startPairing(it.name, it.host) },
                    onPairAddress = model::startPairingFromAddress,
                    onPin = model::onPin,
                    onSubmitPin = model::submitPin,
                    onOpenSaved = model::openSaved,
                    onForget = model::forget,
                    onShowDevices = model::showDevices,
                    onHold = model::hold,
                    onRelease = model::release,
                    onRepair = model::repair,
                    onShowSettings = model::showSettings,
                    onThemeMode = model::setThemeMode,
                )
            }
        }
    }
}
