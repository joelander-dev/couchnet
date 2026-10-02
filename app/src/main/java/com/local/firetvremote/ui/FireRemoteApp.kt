package com.local.firetvremote.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.local.firetvremote.Screen
import com.local.firetvremote.UiState
import com.local.firetvremote.data.SavedDevice
import com.local.firetvremote.net.DiscoveredTv
import com.local.firetvremote.net.RemoteActions

@Composable
fun FireRemoteApp(
    state: UiState,
    onScan: (deep: Boolean) -> Unit,
    onManualAddress: (String) -> Unit,
    onPairDiscovered: (DiscoveredTv) -> Unit,
    onPairAddress: () -> Unit,
    onPin: (String) -> Unit,
    onSubmitPin: () -> Unit,
    onOpenSaved: (SavedDevice) -> Unit,
    onForget: (String) -> Unit,
    onShowDevices: () -> Unit,
    onHold: (String) -> Unit,
    onRelease: () -> Unit,
    onRepair: () -> Unit,
) {
    val screen = state.screen
    if (screen !is Screen.Devices) {
        BackHandler(onBack = onShowDevices)
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Ink)
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        when (screen) {
            Screen.Devices -> DevicesScreen(
                state = state,
                onScan = onScan,
                onManualAddress = onManualAddress,
                onPairDiscovered = onPairDiscovered,
                onPairAddress = onPairAddress,
                onOpenSaved = onOpenSaved,
                onForget = onForget,
            )
            is Screen.Pairing -> PairingScreen(
                state = state,
                name = screen.name,
                host = screen.host,
                onPin = onPin,
                onSubmitPin = onSubmitPin,
                onRetry = { onPairDiscovered(DiscoveredTv(screen.name, screen.host, confirmed = true)) },
                onBack = onShowDevices,
            )
            is Screen.Remote -> RemoteScreen(
                state = state,
                device = screen.device,
                onHold = onHold,
                onRelease = onRelease,
                onBack = onShowDevices,
                onForget = { onForget(screen.device.host) },
                onRepair = onRepair,
            )
        }
    }
}

@Composable
private fun DevicesScreen(
    state: UiState,
    onScan: (deep: Boolean) -> Unit,
    onManualAddress: (String) -> Unit,
    onPairDiscovered: (DiscoveredTv) -> Unit,
    onPairAddress: () -> Unit,
    onOpenSaved: (SavedDevice) -> Unit,
    onForget: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("Fire Remote", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
        Text(
            "Finds and controls a Fire TV on this Wi-Fi only. Nothing is sent over the internet.",
            color = Mist,
            style = MaterialTheme.typography.bodyMedium,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                onClick = { onScan(false) },
                enabled = !state.scanning,
                colors = ButtonDefaults.buttonColors(containerColor = Amber, contentColor = Color(0xFF1A1200)),
            ) {
                Text(if (state.scanning) "Scanning" else "Scan")
            }
            OutlinedButton(onClick = { onScan(true) }, enabled = !state.scanning) {
                Text("Search whole network")
            }
        }
        if (state.scanning) {
            CircularProgressIndicator(modifier = Modifier.size(28.dp), color = Amber, strokeWidth = 3.dp)
        }
        Text(state.scanMessage, color = Mist, style = MaterialTheme.typography.bodyMedium)
        state.error?.let { Text(it, color = Danger) }

        if (state.saved.isNotEmpty()) {
            SectionLabel("Paired")
            state.saved.forEach { device ->
                DeviceRow(
                    title = device.name,
                    subtitle = device.host,
                    action = "Open",
                    onAction = { onOpenSaved(device) },
                    onForget = { onForget(device.host) },
                )
            }
        }

        if (state.discovered.isNotEmpty()) {
            SectionLabel("On this network")
            state.discovered.forEach { device ->
                DeviceRow(
                    title = device.name,
                    subtitle = if (device.confirmed) device.host else "${device.host} · not confirmed",
                    action = "Pair",
                    onAction = { onPairDiscovered(device) },
                )
            }
        }

        SectionLabel("Address")
        OutlinedTextField(
            value = state.manualAddress,
            onValueChange = onManualAddress,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("Fire TV IP address") },
            placeholder = { Text("192.168.1.20") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        )
        Text(
            "On the Fire TV: Settings, My Fire TV, About, Network. Pairing works even if the TV is asleep.",
            color = Mist,
            style = MaterialTheme.typography.bodySmall,
        )
        Button(
            onClick = onPairAddress,
            enabled = state.manualAddress.isNotBlank() && !state.scanning,
            colors = ButtonDefaults.buttonColors(containerColor = Amber, contentColor = Color(0xFF1A1200)),
        ) {
            Text("Pair this address")
        }
    }
}

@Composable
private fun PairingScreen(
    state: UiState,
    name: String,
    host: String,
    onPin: (String) -> Unit,
    onSubmitPin: () -> Unit,
    onRetry: () -> Unit,
    onBack: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        TextButton(onClick = onBack) { Text("Back") }
        Text("Pair $name", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        Text(host, color = Mist)
        Text(
            "A code appears on the TV. Type it here. This stays on your Wi-Fi.",
            color = Mist,
        )
        if (state.pairingBusy) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CircularProgressIndicator(modifier = Modifier.size(22.dp), color = Amber, strokeWidth = 3.dp)
                Text(state.status.ifBlank { "Contacting the Fire TV…" }, color = Mist)
            }
        } else if (state.status.isNotBlank()) {
            Text(state.status, color = Mist)
        }
        OutlinedTextField(
            value = state.pin,
            onValueChange = onPin,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("Code on the TV") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )
        Button(
            onClick = onSubmitPin,
            enabled = state.pin.length >= 4 && !state.pairingBusy,
            colors = ButtonDefaults.buttonColors(containerColor = Amber, contentColor = Color(0xFF1A1200)),
        ) {
            Text("Pair")
        }
        state.error?.let {
            Text(it, color = Danger)
            TextButton(onClick = onRetry) { Text("Show the code again") }
        }
    }
}

@Composable
private fun RemoteScreen(
    state: UiState,
    device: SavedDevice,
    onHold: (String) -> Unit,
    onRelease: () -> Unit,
    onBack: () -> Unit,
    onForget: () -> Unit,
    onRepair: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onBack) { Text("TVs") }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onForget) { Text("Forget") }
        }
        Text(device.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Text(device.host, color = Mist, style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(6.dp))
        StatusLine(state.status)
        Spacer(Modifier.height(16.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(36.dp))
                .background(RemoteBody)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val gap = 10.dp
                val key = ((maxWidth - gap * 2) * 0.56f - gap * 2) / 3
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(gap),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Rocker(
                        modifier = Modifier.weight(0.22f),
                        caption = "VOL",
                        upLabel = "Volume up",
                        downLabel = "Volume down",
                        upAction = RemoteActions.VOLUME_UP,
                        downAction = RemoteActions.VOLUME_DOWN,
                        onHold = onHold,
                        onRelease = onRelease,
                    )
                    Dpad(
                        modifier = Modifier.weight(0.56f),
                        key = key,
                        onHold = onHold,
                        onRelease = onRelease,
                    )
                    Rocker(
                        modifier = Modifier.weight(0.22f),
                        caption = "CH",
                        upLabel = "Channel up",
                        downLabel = "Channel down",
                        upAction = RemoteActions.CHANNEL_UP,
                        downAction = RemoteActions.CHANNEL_DOWN,
                        onHold = onHold,
                        onRelease = onRelease,
                    )
                }
            }
            HoldKey(
                action = RemoteActions.INPUT,
                label = "Input",
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp),
                onHold = onHold,
                onRelease = onRelease,
            ) {
                Text("INPUT", fontWeight = FontWeight.Bold)
            }
            HoldKey(
                action = RemoteActions.BACK,
                label = "Back",
                modifier = Modifier
                    .fillMaxWidth(0.46f)
                    .height(48.dp),
                shape = RoundedCornerShape(14.dp),
                onHold = onHold,
                onRelease = onRelease,
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("BACK", fontWeight = FontWeight.SemiBold)
            }
        }

        state.error?.let { message ->
            Spacer(Modifier.height(14.dp))
            Text(message, color = Danger, textAlign = TextAlign.Center)
            if (state.needsRepair) {
                TextButton(onClick = onRepair) { Text("Pair again") }
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            "Volume, channel, and input reach the TV when Fire TV equipment control is set up.",
            color = Mist,
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 12.dp),
        )
    }
}

@Composable
private fun Dpad(
    modifier: Modifier,
    key: Dp,
    onHold: (String) -> Unit,
    onRelease: () -> Unit,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ArrowKey(RemoteActions.UP, "Up", Icons.Filled.KeyboardArrowUp, key, onHold, onRelease)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            ArrowKey(RemoteActions.LEFT, "Left", Icons.AutoMirrored.Filled.KeyboardArrowLeft, key, onHold, onRelease)
            HoldKey(
                action = RemoteActions.SELECT,
                label = "OK",
                modifier = Modifier.size(key),
                shape = CircleShape,
                onHold = onHold,
                onRelease = onRelease,
            ) {
                Text("OK", fontWeight = FontWeight.Bold)
            }
            ArrowKey(RemoteActions.RIGHT, "Right", Icons.AutoMirrored.Filled.KeyboardArrowRight, key, onHold, onRelease)
        }
        ArrowKey(RemoteActions.DOWN, "Down", Icons.Filled.KeyboardArrowDown, key, onHold, onRelease)
    }
}

@Composable
private fun ArrowKey(
    action: String,
    label: String,
    icon: ImageVector,
    size: Dp,
    onHold: (String) -> Unit,
    onRelease: () -> Unit,
) {
    HoldKey(
        action = action,
        label = label,
        modifier = Modifier.size(size),
        shape = CircleShape,
        onHold = onHold,
        onRelease = onRelease,
    ) {
        Icon(icon, contentDescription = null)
    }
}

@Composable
private fun Rocker(
    modifier: Modifier,
    caption: String,
    upLabel: String,
    downLabel: String,
    upAction: String,
    downAction: String,
    onHold: (String) -> Unit,
    onRelease: () -> Unit,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(caption, color = Mist, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
        HoldKey(
            action = upAction,
            label = upLabel,
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp),
            shape = RoundedCornerShape(18.dp),
            onHold = onHold,
            onRelease = onRelease,
        ) {
            Text("+", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        }
        HoldKey(
            action = downAction,
            label = downLabel,
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp),
            shape = RoundedCornerShape(18.dp),
            onHold = onHold,
            onRelease = onRelease,
        ) {
            Text("–", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun HoldKey(
    action: String,
    label: String,
    modifier: Modifier,
    shape: androidx.compose.ui.graphics.Shape,
    onHold: (String) -> Unit,
    onRelease: () -> Unit,
    content: @Composable () -> Unit,
) {
    var pressed by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current
    val color = if (pressed) KeyDown else KeyRest
    val contentColor = if (pressed) Color(0xFF1A1200) else Color.White
    Row(
        modifier = modifier
            .clip(shape)
            .background(color)
            .border(1.dp, Color.White.copy(alpha = 0.06f), shape)
            .semantics(mergeDescendants = true) {
                role = Role.Button
                contentDescription = label
            }
            .pointerInput(action) {
                awaitEachGesture {
                    awaitFirstDown()
                    pressed = true
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onHold(action)
                    try {
                        waitForUpOrCancellation()
                    } finally {
                        pressed = false
                        onRelease()
                    }
                }
            },
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        androidx.compose.runtime.CompositionLocalProvider(
            androidx.compose.material3.LocalContentColor provides contentColor,
        ) {
            content()
        }
    }
}

@Composable
private fun StatusLine(status: String) {
    val live = status == "Connected"
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(
            Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(if (live) Color(0xFF3DDC84) else Color(0xFF6E6E78)),
        )
        Text(if (live) "Connected on this Wi-Fi" else "Ready", color = Mist, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
}

@Composable
private fun DeviceRow(
    title: String,
    subtitle: String,
    action: String,
    onAction: () -> Unit,
    onForget: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(RemoteBody)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = Mist, style = MaterialTheme.typography.bodySmall)
        }
        TextButton(onClick = onAction) { Text(action) }
        if (onForget != null) {
            TextButton(onClick = onForget) { Text("Forget") }
        }
    }
}
