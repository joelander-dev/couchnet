package com.local.firetvremote

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.local.firetvremote.data.DeviceStore
import com.local.firetvremote.data.SavedDevice
import com.local.firetvremote.net.CommandResult
import com.local.firetvremote.net.DiscoveredTv
import com.local.firetvremote.net.FireTvClient
import com.local.firetvremote.net.FireTvDiscovery
import com.local.firetvremote.net.PrivateAddress
import com.local.firetvremote.net.RemoteActions
import com.local.firetvremote.net.isGeneratedTvName
import com.local.firetvremote.net.buildHttpClient
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

sealed interface Screen {
    data object Devices : Screen
    data class Pairing(val name: String, val host: String) : Screen
    data class Remote(val device: SavedDevice) : Screen
}

data class UiState(
    val screen: Screen = Screen.Devices,
    val scanning: Boolean = false,
    val scanMessage: String = "Scan for a Fire TV on this Wi-Fi, or type its address.",
    val discovered: List<DiscoveredTv> = emptyList(),
    val saved: List<SavedDevice> = emptyList(),
    val manualAddress: String = "",
    val pin: String = "",
    val pairingBusy: Boolean = false,
    val status: String = "",
    val error: String? = null,
    val needsRepair: Boolean = false,
)

class RemoteViewModel(app: Application) : AndroidViewModel(app) {
    private val store = DeviceStore(app)
    private val http = buildHttpClient()
    private val client = FireTvClient(http)
    private val discovery = FireTvDiscovery(app, http)

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var scanJob: Job? = null
    private var repeatJob: Job? = null
    private var holdAction: String? = null

    init {
        val saved = store.load()
        val last = saved.firstOrNull { it.host == store.lastHost() } ?: saved.firstOrNull()
        _state.update { it.copy(saved = saved, screen = if (last != null) Screen.Remote(last) else Screen.Devices) }
        if (last != null) connect(last)
        viewModelScope.launch { refreshGeneratedNames(saved) }
    }

    fun onManualAddress(value: String) {
        _state.update { it.copy(manualAddress = value.take(40)) }
    }

    fun onPin(value: String) {
        _state.update { it.copy(pin = value.filter { char -> char.isDigit() }.take(8), error = null) }
    }

    fun showDevices() {
        release()
        _state.update {
            it.copy(
                screen = Screen.Devices,
                error = null,
                needsRepair = false,
                pin = "",
                pairingBusy = false,
            )
        }
    }

    fun scan(deep: Boolean = false) {
        scanJob?.cancel()
        scanJob = viewModelScope.launch {
            _state.update {
                it.copy(
                    scanning = true,
                    error = null,
                    scanMessage = if (deep) {
                        "Checking addresses on your Wi-Fi…"
                    } else {
                        "Looking for Fire TVs…"
                    },
                )
            }
            val found = try {
                discovery.scan(deep)
            } catch (_: Exception) {
                emptyList()
            }
            val saved = renameGeneratedDevices(store.load(), found)
            _state.update {
                it.copy(
                    scanning = false,
                    discovered = found,
                    saved = saved,
                    screen = renamedScreen(it.screen, saved),
                    scanMessage = when {
                        found.isNotEmpty() -> "Choose a Fire TV to pair."
                        deep -> "None found. Enter the address from Settings, My Fire TV, About, Network."
                        else -> "None answered the search. Try a deeper search, or enter the address."
                    },
                )
            }
        }
    }

    private suspend fun refreshGeneratedNames(devices: List<SavedDevice>) {
        val renamed = devices.map { device ->
            if (!isGeneratedTvName(device.name)) {
                device
            } else {
                val name = discovery.friendlyName(device.host)
                if (name.isNullOrBlank() || isGeneratedTvName(name)) device else device.copy(name = name)
            }
        }
        if (renamed == devices) return
        store.save(renamed)
        _state.update { it.copy(saved = renamed, screen = renamedScreen(it.screen, renamed)) }
    }

    private fun renameGeneratedDevices(
        saved: List<SavedDevice>,
        found: List<DiscoveredTv>,
    ): List<SavedDevice> {
        val names = found.filter { it.confirmed && !isGeneratedTvName(it.name) }.associate { it.host to it.name }
        if (names.isEmpty()) return saved
        val renamed = saved.map { device ->
            val name = names[device.host]
            if (name != null && isGeneratedTvName(device.name)) device.copy(name = name) else device
        }
        if (renamed != saved) store.save(renamed)
        return renamed
    }

    private fun renamedScreen(screen: Screen, saved: List<SavedDevice>): Screen {
        val remote = screen as? Screen.Remote ?: return screen
        val match = saved.firstOrNull { it.host == remote.device.host } ?: return screen
        return if (match.name == remote.device.name) screen else Screen.Remote(match)
    }

    fun startPairing(name: String, host: String) {
        val local = PrivateAddress.normalize(host) ?: run {
            _state.update { it.copy(error = "Enter an address on your home network, such as 192.168.1.20.") }
            return
        }
        _state.update {
            it.copy(
                screen = Screen.Pairing(name = name.ifBlank { "Fire TV" }, host = local),
                pin = "",
                error = null,
                needsRepair = false,
                pairingBusy = true,
                status = "Asking the TV to show a code…",
            )
        }
        viewModelScope.launch { showPinOnTv() }
    }

    fun startPairingFromAddress() {
        val raw = _state.value.manualAddress
        startPairing(name = "Fire TV", host = raw)
    }

    fun submitPin() {
        val screen = _state.value.screen as? Screen.Pairing ?: return
        val pin = _state.value.pin
        if (pin.length < 4) {
            _state.update { it.copy(error = "Enter the code shown on the TV.") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(pairingBusy = true, error = null, status = "Checking the code…") }
            val token = client.verifyPin(screen.host, pin)
            if (token == null) {
                _state.update {
                    it.copy(
                        pairingBusy = false,
                        status = "",
                        error = "That code was not accepted. Check the TV and try again.",
                    )
                }
                return@launch
            }
            val device = SavedDevice(name = screen.name, host = screen.host, token = token)
            val updated = store.load().filterNot { it.host == device.host } + device
            store.save(updated)
            store.setLastHost(device.host)
            _state.update {
                it.copy(
                    saved = updated,
                    pairingBusy = false,
                    pin = "",
                    // The TV just answered the pairing request, so it is reachable.
                    status = STATUS_CONNECTED,
                    error = null,
                    needsRepair = false,
                    screen = Screen.Remote(device),
                )
            }
        }
    }

    fun openSaved(device: SavedDevice) {
        store.setLastHost(device.host)
        _state.update { it.copy(screen = Screen.Remote(device), error = null, needsRepair = false) }
        connect(device)
    }

    /**
     * Wakes the TV's remote service and uses the answer to show the connection
     * status right away, instead of waiting for the first button press.
     * A missed wake only falls back to the neutral status; the first button
     * press still confirms the connection.
     */
    private fun connect(device: SavedDevice) {
        _state.update { it.copy(status = STATUS_CONNECTING) }
        viewModelScope.launch {
            val reachable = client.wake(device.host)
            _state.update {
                val sameTv = (it.screen as? Screen.Remote)?.device?.host == device.host
                if (sameTv && it.status == STATUS_CONNECTING) {
                    it.copy(status = if (reachable) STATUS_CONNECTED else "")
                } else {
                    it
                }
            }
        }
    }

    fun forget(host: String) {
        val updated = store.load().filterNot { it.host == host }
        store.save(updated)
        if (store.lastHost() == host) store.setLastHost(updated.firstOrNull()?.host.orEmpty())
        val screen = _state.value.screen
        val leavingRemote = screen is Screen.Remote && screen.device.host == host
        _state.update {
            it.copy(
                saved = updated,
                screen = if (leavingRemote) Screen.Devices else it.screen,
            )
        }
    }

    fun hold(action: String) {
        if (!RemoteActions.isKnown(action)) return
        val device = (_state.value.screen as? Screen.Remote)?.device ?: return
        if (holdAction == action && repeatJob?.isActive == true) return
        release()
        holdAction = action
        val repeating = action in RemoteActions.repeating
        repeatJob = viewModelScope.launch {
            if (!send(device, action) || !repeating) return@launch
            delay(380)
            while (isActive && holdAction == action) {
                if (!send(device, action)) break
                delay(140)
            }
        }
    }

    fun release() {
        holdAction = null
        repeatJob?.cancel()
        repeatJob = null
    }

    fun repair() {
        val device = (_state.value.screen as? Screen.Remote)?.device
            ?: (_state.value.screen as? Screen.Pairing)?.let { SavedDevice(it.name, it.host, "") }
            ?: return
        startPairing(device.name, device.host)
    }

    private suspend fun showPinOnTv() {
        val screen = _state.value.screen as? Screen.Pairing ?: return
        client.wake(screen.host)
        delay(400)
        val shown = client.requestPin(screen.host, "Couchnet")
        if (!shown) {
            client.wake(screen.host)
            delay(800)
        }
        val retry = shown || client.requestPin(screen.host, "Couchnet")
        _state.update {
            if (it.screen !is Screen.Pairing) return@update it
            if (retry) {
                it.copy(pairingBusy = false, status = "Enter the 4-digit code on the TV.")
            } else {
                it.copy(
                    pairingBusy = false,
                    status = "",
                    error = "Couldn't reach that Fire TV. Use the same Wi-Fi, then try again.",
                )
            }
        }
    }

    private suspend fun send(device: SavedDevice, action: String): Boolean {
        return when (val result = client.command(device.host, device.token, action)) {
            CommandResult.Ok -> {
                _state.update { it.copy(status = STATUS_CONNECTED, error = null, needsRepair = false) }
                true
            }
            CommandResult.Cancelled -> false
            CommandResult.AuthExpired -> {
                _state.update {
                    it.copy(
                        error = "This Fire TV forgot the remote. Pair it again.",
                        needsRepair = true,
                        status = "",
                    )
                }
                false
            }
            is CommandResult.Failed -> {
                _state.update { if (it.error == null) it.copy(error = result.message, status = "") else it }
                false
            }
        }
    }
}

const val STATUS_CONNECTING = "Connecting"
const val STATUS_CONNECTED = "Connected"
