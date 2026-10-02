package com.local.firetvremote.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

sealed interface CommandResult {
    data object Ok : CommandResult
    data object AuthExpired : CommandResult
    data object Cancelled : CommandResult
    data class Failed(val message: String) : CommandResult
}

/**
 * Talks to the Fire TV remote service on the local network.
 * Wake is HTTP on port 8009. Pairing and keys are HTTPS on port 8080.
 * The stick shows a code on screen; verifying it returns a client token.
 */
class FireTvClient(
    private val http: OkHttpClient = buildHttpClient(),
    private val permitLoopback: Boolean = false,
) {
    @Volatile
    private var activeCall: Call? = null

    fun cancelActive() {
        activeCall?.cancel()
    }

    suspend fun wake(host: String): Boolean = withContext(Dispatchers.IO) {
        val request = try {
            buildWakeRequest(host, permitLoopback)
        } catch (error: IllegalArgumentException) {
            return@withContext false
        }
        execute(request) { response ->
            response.code in listOf(200, 201, 204)
        } == true
    }

    suspend fun requestPin(host: String, friendlyName: String): Boolean = withContext(Dispatchers.IO) {
        val safeName = friendlyName.filter { it.isLetterOrDigit() || it == ' ' || it == '-' }.take(40)
            .ifBlank { "Fire Remote" }
        val body = """{"friendlyName":"$safeName"}"""
        val request = try {
            buildJsonRequest(
                host = host,
                path = "v1/FireTV/pin/display",
                json = body,
                token = null,
                permitLoopback = permitLoopback,
            )
        } catch (error: IllegalArgumentException) {
            return@withContext false
        }
        execute(request) { it.code == 200 } == true
    }

    suspend fun verifyPin(host: String, pin: String): String? = withContext(Dispatchers.IO) {
        if (pin.length !in 4..8 || pin.any { !it.isDigit() }) return@withContext null
        val request = try {
            buildJsonRequest(
                host = host,
                path = "v1/FireTV/pin/verify",
                json = """{"pin":"$pin"}""",
                token = null,
                permitLoopback = permitLoopback,
            )
        } catch (error: IllegalArgumentException) {
            return@withContext null
        }
        execute(request) { response ->
            if (response.code != 200) return@execute null
            tokenFromPinResponse(response.body?.string(), response.header("X-Client-Token"))
        }
    }

    suspend fun command(host: String, token: String, action: String): CommandResult = withContext(Dispatchers.IO) {
        if (!RemoteActions.isKnown(action) || token.isBlank()) {
            return@withContext CommandResult.Failed("That button is not available.")
        }
        val request = try {
            if (action in RemoteActions.media) {
                buildMediaRequest(host, token, action, permitLoopback)
            } else {
                buildCommandRequest(host, token, action, permitLoopback)
            }
        } catch (error: IllegalArgumentException) {
            return@withContext CommandResult.Failed("That address is not on your home network.")
        }
        val call = http.newCall(request)
        activeCall = call
        try {
            call.execute().use { response ->
                val body = response.body?.string().orEmpty()
                val rejected = body.contains("Bad arguments", ignoreCase = true)
                when {
                    response.code == 401 || response.code == 403 -> CommandResult.AuthExpired
                    rejected -> CommandResult.Failed("The Fire TV did not accept that button.")
                    response.code == 200 || response.code == 500 -> CommandResult.Ok
                    else -> CommandResult.Failed("The Fire TV returned an unexpected response.")
                }
            }
        } catch (error: IOException) {
            if (call.isCanceled()) {
                CommandResult.Cancelled
            } else {
                CommandResult.Failed("Fire TV didn't respond. Check that this phone is on the same Wi-Fi.")
            }
        } finally {
            if (activeCall === call) activeCall = null
        }
    }

    private fun <T> execute(request: Request, block: (okhttp3.Response) -> T): T? {
        val call = http.newCall(request)
        activeCall = call
        return try {
            call.execute().use(block)
        } catch (error: IOException) {
            null
        } finally {
            if (activeCall === call) activeCall = null
        }
    }
}

internal fun buildHttpClient(permitLoopback: Boolean = false): OkHttpClient {
    val trust = object : X509TrustManager {
        override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
        override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
        override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
    }
    val context = SSLContext.getInstance("TLS")
    context.init(null, arrayOf<TrustManager>(trust), SecureRandom())
    return OkHttpClient.Builder()
        .sslSocketFactory(context.socketFactory, trust)
        .hostnameVerifier { hostname, _ -> PrivateAddress.isPrivate(hostname, permitLoopback) }
        .followRedirects(false)
        .followSslRedirects(false)
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .writeTimeout(5, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            val host = chain.request().url.host
            if (!PrivateAddress.isPrivate(host, permitLoopback)) {
                throw IOException("Blocked a request that was not on the local network.")
            }
            chain.proceed(chain.request())
        }
        .build()
}

internal fun buildWakeRequest(host: String, permitLoopback: Boolean = false): Request {
    requireLocal(host, permitLoopback)
    return Request.Builder()
        .url("http://$host:8009/apps/FireTVRemote")
        .header("User-Agent", USER_AGENT)
        .header("Content-Type", "text/plain; charset=utf-8")
        .post(ByteArray(0).toRequestBody(null))
        .build()
}

internal fun buildMediaRequest(
    host: String,
    token: String,
    action: String,
    permitLoopback: Boolean = false,
): Request {
    requireLocal(host, permitLoopback)
    val (queryAction, json) = when (action) {
        RemoteActions.PLAY -> "play" to "{}"
        RemoteActions.PAUSE -> "pause" to "{}"
        RemoteActions.FAST_FORWARD -> "scan" to SCAN_FORWARD
        RemoteActions.REWIND -> "scan" to SCAN_BACK
        else -> throw IllegalArgumentException("Not a media button")
    }
    return Request.Builder()
        .url("https://$host:8080/v1/media?action=$queryAction")
        .header("User-Agent", USER_AGENT)
        .header("X-Api-Key", API_KEY)
        .header("X-Client-Token", token)
        .post(json.toRequestBody(JSON))
        .build()
}

internal fun buildCommandRequest(
    host: String,
    token: String,
    action: String,
    permitLoopback: Boolean = false,
): Request {
    requireLocal(host, permitLoopback)
    require(RemoteActions.isKnown(action))
    return Request.Builder()
        .url("https://$host:8080/v1/FireTV?action=$action")
        .header("User-Agent", USER_AGENT)
        .header("X-Api-Key", API_KEY)
        .header("X-Client-Token", token)
        .post("""{"keyActionType":"keyDownUp"}""".toRequestBody(JSON))
        .build()
}

internal fun buildJsonRequest(
    host: String,
    path: String,
    json: String,
    token: String?,
    permitLoopback: Boolean,
): Request {
    requireLocal(host, permitLoopback)
    val builder = Request.Builder()
        .url("https://$host:8080/$path")
        .header("User-Agent", USER_AGENT)
        .header("X-Api-Key", API_KEY)
        .post(json.toRequestBody(JSON))
    if (!token.isNullOrBlank()) builder.header("X-Client-Token", token)
    return builder.build()
}

private fun requireLocal(host: String, permitLoopback: Boolean) {
    require(PrivateAddress.isPrivate(host, permitLoopback)) {
        "Refusing non-local address"
    }
}

private const val API_KEY = "0987654321"
private const val USER_AGENT = "okhttp/4.10.0"
private val JSON = "application/json; charset=utf-8".toMediaType()
private const val SCAN_FORWARD =
    """{"direction":"forward","keyAction":{"keyActionType":"keyDown"}}"""
private const val SCAN_BACK =
    """{"direction":"back","keyAction":{"keyActionType":"keyDown"}}"""
