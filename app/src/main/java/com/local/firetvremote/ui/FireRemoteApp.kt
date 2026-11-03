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
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Shape
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
            .padding(horizontal = 20.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Couchnet", color = Name, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text(
            "On this Wi-Fi only. Nothing leaves the local network.",
            color = Mist,
            style = MaterialTheme.typography.bodyMedium,
        )
        Button(
            onClick = { onScan(false) },
            enabled = !state.scanning,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Amber, contentColor = Color(0xFF1A1200)),
        ) {
            Text(if (state.scanning) "Scanning" else "Scan for Fire TVs", fontWeight = FontWeight.SemiBold)
        }
        TextButton(
            onClick = { onScan(true) },
            enabled = !state.scanning,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        ) {
            Text("Search the whole network")
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

        val unpaired = state.discovered.filter { found ->
            state.saved.none { it.host == found.host }
        }
        if (unpaired.isNotEmpty()) {
            SectionLabel("On this network")
            unpaired.forEach { device ->
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
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Amber, contentColor = Color(0xFF1A1200)),
        ) {
            Text("Pair this address", fontWeight = FontWeight.SemiBold)
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
        TextButton(onClick = onBack) { Text("Devices") }
        Text(name, color = Name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(host, color = Mist)
        Text(
            "Enter the code shown on the TV. Pairing stays on your Wi-Fi.",
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
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Amber, contentColor = Color(0xFF1A1200)),
        ) {
            Text("Pair", fontWeight = FontWeight.SemiBold)
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
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onBack) { Text("Devices") }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onForget) { Text("Forget") }
        }
        Text(
            device.name,
            color = Name,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(4.dp))
        StatusLine(state.status)
        Spacer(Modifier.height(18.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(40.dp))
                .background(RemoteBody)
                .border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(40.dp))
                .padding(horizontal = 18.dp, vertical = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            RoundKey(
                action = RemoteActions.POWER,
                label = "Power",
                icon = Icons.Filled.PowerSettingsNew,
                size = 56.dp,
                restContent = Amber,
                onHold = onHold,
                onRelease = onRelease,
            )
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val rockerWidth = 64.dp
                val gap = 12.dp
                val pad = (maxWidth - rockerWidth * 2 - gap * 2).coerceIn(180.dp, 260.dp)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Rocker(
                        width = rockerWidth,
                        caption = "VOL",
                        upLabel = "Volume up",
                        downLabel = "Volume down",
                        upAction = RemoteActions.VOLUME_UP,
                        downAction = RemoteActions.VOLUME_DOWN,
                        onHold = onHold,
                        onRelease = onRelease,
                    )
                    Dpad(size = pad, onHold = onHold, onRelease = onRelease)
                    Rocker(
                        width = rockerWidth,
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RoundKey(RemoteActions.REWIND, "Rewind", Icons.Filled.FastRewind, 52.dp, onHold, onRelease)
                RoundKey(RemoteActions.PLAY, "Play", Icons.Filled.PlayArrow, 56.dp, onHold, onRelease)
                RoundKey(RemoteActions.PAUSE, "Pause", Icons.Filled.Pause, 56.dp, onHold, onRelease)
                RoundKey(RemoteActions.FAST_FORWARD, "Fast-forward", Icons.Filled.FastForward, 52.dp, onHold, onRelease)
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RoundKey(RemoteActions.BACK, "Back", Icons.AutoMirrored.Filled.ArrowBack, 56.dp, onHold, onRelease)
                RoundKey(RemoteActions.HOME, "Home", Icons.Filled.Home, 56.dp, onHold, onRelease)
                RoundKey(RemoteActions.OPTIONS, "Options", Icons.Filled.Menu, 56.dp, onHold, onRelease)
            }
        }

        state.error?.let { message ->
            Spacer(Modifier.height(14.dp))
            Text(message, color = Danger, textAlign = TextAlign.Center)
            if (state.needsRepair) {
                TextButton(onClick = onRepair) { Text("Pair again") }
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(
            "Volume, channel, and power reach the TV when Fire TV equipment control is set up.",
            color = Mist,
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 8.dp),
        )
    }
}

@Composable
private fun Dpad(
    size: Dp,
    onHold: (String) -> Unit,
    onRelease: () -> Unit,
) {
    val arrow = size * 0.28f
    val ok = size * 0.34f
    Box(modifier = Modifier.size(size), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .fillMaxSize()
                .clip(CircleShape)
                .background(Pad)
                .border(1.dp, Color.White.copy(alpha = 0.05f), CircleShape),
        )
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceEvenly,
        ) {
            GhostKey(RemoteActions.UP, "Up", Icons.Filled.KeyboardArrowUp, arrow, onHold, onRelease)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                GhostKey(RemoteActions.LEFT, "Left", Icons.AutoMirrored.Filled.KeyboardArrowLeft, arrow, onHold, onRelease)
                HoldKey(
                    action = RemoteActions.SELECT,
                    label = "Select",
                    modifier = Modifier.size(ok),
                    shape = CircleShape,
                    rest = KeyRest,
                    onHold = onHold,
                    onRelease = onRelease,
                ) {
                    Text("OK", fontWeight = FontWeight.Bold)
                }
                GhostKey(RemoteActions.RIGHT, "Right", Icons.AutoMirrored.Filled.KeyboardArrowRight, arrow, onHold, onRelease)
            }
            GhostKey(RemoteActions.DOWN, "Down", Icons.Filled.KeyboardArrowDown, arrow, onHold, onRelease)
        }
    }
}

@Composable
private fun GhostKey(
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
        rest = Color.Transparent,
        onHold = onHold,
        onRelease = onRelease,
    ) {
        Icon(icon, contentDescription = null)
    }
}

@Composable
private fun RoundKey(
    action: String,
    label: String,
    icon: ImageVector,
    size: Dp,
    onHold: (String) -> Unit,
    onRelease: () -> Unit,
    restContent: Color = Color.White,
) {
    HoldKey(
        action = action,
        label = label,
        modifier = Modifier.size(size),
        shape = CircleShape,
        restContent = restContent,
        onHold = onHold,
        onRelease = onRelease,
    ) {
        Icon(icon, contentDescription = null)
    }
}

@Composable
private fun Rocker(
    width: Dp,
    caption: String,
    upLabel: String,
    downLabel: String,
    upAction: String,
    downAction: String,
    onHold: (String) -> Unit,
    onRelease: () -> Unit,
) {
    Column(
        modifier = Modifier
            .width(width)
            .clip(RoundedCornerShape(percent = 50))
            .background(Pad)
            .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(percent = 50)),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        HoldKey(
            action = upAction,
            label = upLabel,
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp),
            shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
            rest = Color.Transparent,
            onHold = onHold,
            onRelease = onRelease,
        ) {
            Text("+", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Medium)
        }
        Text(caption, color = Mist, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
        HoldKey(
            action = downAction,
            label = downLabel,
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp),
            shape = RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp),
            rest = Color.Transparent,
            onHold = onHold,
            onRelease = onRelease,
        ) {
            Text("–", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun HoldKey(
    action: String,
    label: String,
    modifier: Modifier,
    shape: Shape,
    onHold: (String) -> Unit,
    onRelease: () -> Unit,
    rest: Color = KeyRest,
    restContent: Color = Color.White,
    content: @Composable () -> Unit,
) {
    var pressed by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current
    val color = if (pressed) KeyDown else rest
    val contentColor = if (pressed) Color(0xFF1A1200) else restContent
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
    Text(text, color = Name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
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
            .clip(RoundedCornerShape(20.dp))
            .background(RemoteBody)
            .border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(20.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = Name, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = Mist, style = MaterialTheme.typography.bodySmall)
        }
        TextButton(onClick = onAction) { Text(action) }
        if (onForget != null) {
            TextButton(onClick = onForget) { Text("Forget") }
        }
    }
}
