package com.local.firetvremote.net

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.DatagramPacket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.MulticastSocket
import java.net.NetworkInterface
import java.net.Socket
import java.net.SocketTimeoutException

data class DiscoveredTv(
    val name: String,
    val host: String,
    val confirmed: Boolean,
)

private sealed interface Identified {
    data class Fire(val tv: DiscoveredTv) : Identified
    data object NotFire : Identified
    data object Unknown : Identified
}

class FireTvDiscovery(
    context: Context,
    private val http: OkHttpClient,
) {
    private val appContext = context.applicationContext

    suspend fun friendlyName(host: String): String? = withContext(Dispatchers.IO) {
        if (!PrivateAddress.isPrivate(host)) return@withContext null
        preferLan()
        when (val identified = identify("http://$host:8008/description.xml", host)) {
            is Identified.Fire -> identified.tv.name
            else -> null
        }
    }

    suspend fun scan(deep: Boolean): List<DiscoveredTv> = withContext(Dispatchers.IO) {
        preferLan()
        val fromSsdp = ssdp()
        if (fromSsdp.isNotEmpty() && !deep) return@withContext fromSsdp
        val scanned = if (deep || fromSsdp.isEmpty()) subnetScan() else emptyList()
        (fromSsdp + scanned).distinctBy { it.host }
    }

    private fun ssdp(): List<DiscoveredTv> {
        val wifi = appContext.getSystemService(WifiManager::class.java)
        val lock = wifi?.createMulticastLock("fire-remote-ssdp")?.apply {
            setReferenceCounted(false)
        }
        val found = linkedMapOf<String, DiscoveredTv>()
        val socket = MulticastSocket(null)
        try {
            lock?.acquire()
            socket.reuseAddress = true
            socket.timeToLive = 4
            val localIp = localIpv4()
            if (localIp != null) {
                socket.bind(InetSocketAddress(localIp, 0))
                val iface = NetworkInterface.getByInetAddress(InetAddress.getByName(localIp))
                if (iface != null) {
                    try {
                        socket.joinGroup(
                            InetSocketAddress(InetAddress.getByName("239.255.255.250"), 1900),
                            iface,
                        )
                    } catch (_: Exception) {
                    }
                }
            } else {
                socket.bind(InetSocketAddress(0))
            }
            lanNetwork()?.bindSocket(socket)
            val target = InetAddress.getByName("239.255.255.250")
            val searches = listOf(
                "urn:dial-multiscreen-org:service:dial:1",
                "upnp:rootdevice",
            )
            repeat(2) {
                for (searchTarget in searches) {
                    val payload = msearch("239.255.255.250", searchTarget)
                    socket.send(DatagramPacket(payload, payload.size, target, 1900))
                }
            }
            val deadline = System.currentTimeMillis() + 2500
            val buffer = ByteArray(8192)
            while (System.currentTimeMillis() < deadline) {
                val remaining = (deadline - System.currentTimeMillis()).toInt().coerceAtLeast(1)
                socket.soTimeout = remaining
                val packet = DatagramPacket(buffer, buffer.size)
                try {
                    socket.receive(packet)
                } catch (_: SocketTimeoutException) {
                    break
                }
                val message = String(packet.data, 0, packet.length, Charsets.UTF_8)
                val host = packet.address?.hostAddress?.let { hostFromAddress(it) } ?: continue
                rememberSsdp(found, host, message)
            }
        } catch (_: Exception) {
            return found.values.toList()
        } finally {
            socket.close()
            if (lock?.isHeld == true) lock.release()
        }
        return found.values.toList()
    }

    private fun rememberSsdp(found: MutableMap<String, DiscoveredTv>, host: String, message: String) {
        if (found[host]?.confirmed == true) return
        val location = locationFromSsdp(message)
        val fromDescription = if (location != null) {
            val describedHost = hostFromHttpUrl(location) ?: host
            identify(location, describedHost)
        } else {
            null
        }
        if (fromDescription is Identified.Fire) {
            found[fromDescription.tv.host] = fromDescription.tv
            return
        }
        val advertised = ssdpHeader(message, "X-Friendly-Name")?.let(::decodeAdvertisedName)
        if (!advertised.isNullOrBlank() && ssdpLooksLikeFireTv(message)) {
            found[host] = DiscoveredTv(advertised, host, confirmed = true)
            return
        }
        if (fromDescription is Identified.Unknown && !found.containsKey(host)) {
            found[host] = DiscoveredTv(advertised ?: "Fire TV", host, confirmed = false)
        }
    }

    private suspend fun subnetScan(): List<DiscoveredTv> = coroutineScope {
        val prefixes = localPrefixes()
        if (prefixes.isEmpty()) return@coroutineScope emptyList()
        val gate = Semaphore(32)
        prefixes.flatMap { prefix ->
            (1..254).map { last ->
                async {
                    gate.withPermit {
                        val host = "$prefix.$last"
                        if (!portOpen(host, 8009)) return@withPermit null
                        when (val identified = identify("http://$host:8008/description.xml", host)) {
                            is Identified.Fire -> identified.tv
                            Identified.Unknown -> DiscoveredTv("Possible Fire TV", host, confirmed = false)
                            Identified.NotFire -> null
                        }
                    }
                }
            }
        }.awaitAll().filterNotNull().distinctBy { it.host }
    }

    private fun identify(url: String, host: String): Identified {
        if (!PrivateAddress.isPrivate(host)) return Identified.NotFire
        val urls = buildList {
            add(url)
            add("http://$host:60000/dd.xml")
            add("http://$host:8008/description.xml")
            add("http://$host:8008/ssdp/device-desc.xml")
            add("http://$host:8008/dial/dd.xml")
        }.distinct()
        readDescription(urls, host)?.let { return it }
        for (message in unicastReplies(host)) {
            val location = locationFromSsdp(message)
            if (location != null) {
                readDescription(listOf(location), host)?.let { return it }
            }
            val advertised = ssdpHeader(message, "X-Friendly-Name")?.let(::decodeAdvertisedName)
            if (!advertised.isNullOrBlank() && ssdpLooksLikeFireTv(message)) {
                return Identified.Fire(DiscoveredTv(advertised, host, confirmed = true))
            }
        }
        return Identified.Unknown
    }

    private fun readDescription(urls: List<String>, host: String): Identified? {
        var sawOtherDevice = false
        for (candidate in urls) {
            val body = fetchBody(candidate) ?: continue
            val description = parseDialDescription(body)
            val hasFields = description.friendlyName != null ||
                description.manufacturer != null ||
                description.modelName != null
            if (!hasFields) continue
            if (!isFireTv(description)) {
                sawOtherDevice = true
                continue
            }
            val name = description.friendlyName?.takeIf { it.isNotBlank() }
                ?: description.modelName?.takeIf { it.isNotBlank() }
                ?: "Fire TV"
            return Identified.Fire(DiscoveredTv(name = name, host = host, confirmed = true))
        }
        return if (sawOtherDevice) Identified.NotFire else null
    }

    private fun fetchBody(startUrl: String): String? {
        var url = startUrl
        for (attempt in 0 until 4) {
            val host = hostFromHttpUrl(url) ?: return null
            if (!PrivateAddress.isPrivate(host)) return null
            try {
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", "okhttp/4.10.0")
                    .get()
                    .build()
                var redirected: String? = null
                val body = http.newCall(request).execute().use { response ->
                    if (response.code in 300..399) {
                        val next = response.header("Location") ?: return null
                        redirected = response.request.url.resolve(next)?.toString()
                        return@use null
                    }
                    if (!response.isSuccessful) return null
                    response.body?.string()
                }
                val nextUrl = redirected
                if (nextUrl != null) {
                    url = nextUrl
                    continue
                }
                return body
            } catch (_: Exception) {
                return null
            }
        }
        return null
    }

    private fun unicastReplies(host: String): List<String> {
        val replies = mutableListOf<String>()
        val targets = listOf(
            "urn:dial-multiscreen-org:service:dial:1",
            "upnp:rootdevice",
        )
        for (target in targets) {
            replies += unicastSearch(host, target)
        }
        return replies
    }

    private fun unicastSearch(host: String, searchTarget: String): List<String> {
        val payload = msearch(host, searchTarget)
        return try {
            MulticastSocket(null).use { socket ->
                socket.reuseAddress = true
                localIpv4()?.let { socket.bind(InetSocketAddress(it, 0)) } ?: socket.bind(InetSocketAddress(0))
                socket.soTimeout = 400
                lanNetwork()?.bindSocket(socket)
                socket.send(
                    DatagramPacket(
                        payload,
                        payload.size,
                        InetAddress.getByName(host),
                        1900,
                    ),
                )
                val found = mutableListOf<String>()
                val deadline = System.currentTimeMillis() + 900
                val buffer = ByteArray(8192)
                while (System.currentTimeMillis() < deadline && found.size < 4) {
                    val remaining = (deadline - System.currentTimeMillis()).toInt().coerceAtLeast(1)
                    socket.soTimeout = remaining
                    val packet = DatagramPacket(buffer, buffer.size)
                    try {
                        socket.receive(packet)
                    } catch (_: SocketTimeoutException) {
                        break
                    }
                    found += String(packet.data, 0, packet.length, Charsets.UTF_8)
                }
                found
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun msearch(host: String, searchTarget: String): ByteArray {
        return buildString {
            append("M-SEARCH * HTTP/1.1\r\n")
            append("HOST: $host:1900\r\n")
            append("MAN: \"ssdp:discover\"\r\n")
            append("MX: 1\r\n")
            append("ST: $searchTarget\r\n")
            append("\r\n")
        }.toByteArray()
    }

    private fun hostFromAddress(address: String): String? {
        return address.removePrefix("/").takeIf { PrivateAddress.isPrivate(it) }
    }

    private fun portOpen(host: String, port: Int): Boolean {
        return try {
            Socket().use { socket ->
                lanNetwork()?.bindSocket(socket)
                socket.connect(InetSocketAddress(host, port), 250)
                true
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun localIpv4(): String? {
        return localAddresses().firstOrNull()
    }

    private fun localPrefixes(): List<String> {
        return localAddresses()
            .map { it.substringBeforeLast('.') }
            .distinct()
            .take(2)
    }

    private fun localAddresses(): List<String> {
        return try {
            NetworkInterface.getNetworkInterfaces().toList()
                .filter { it.isUp && !it.isLoopback }
                .flatMap { iface -> iface.inetAddresses.toList() }
                .filterIsInstance<Inet4Address>()
                .mapNotNull { it.hostAddress }
                .filter { PrivateAddress.isPrivate(it) }
                .distinct()
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun preferLan() {
        val network = lanNetwork() ?: return
        val manager = appContext.getSystemService(ConnectivityManager::class.java) ?: return
        try {
            manager.bindProcessToNetwork(network)
        } catch (_: SecurityException) {
        }
    }

    @Suppress("DEPRECATION")
    private fun lanNetwork(): Network? {
        val manager = appContext.getSystemService(ConnectivityManager::class.java) ?: return null
        return manager.allNetworks.firstOrNull { network ->
            val caps = manager.getNetworkCapabilities(network) ?: return@firstOrNull false
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
        }
    }
}
