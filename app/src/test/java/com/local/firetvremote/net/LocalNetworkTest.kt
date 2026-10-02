package com.local.firetvremote.net

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalNetworkTest {
    @Test
    fun acceptsHomeNetworkAddresses() {
        assertTrue(PrivateAddress.isPrivate("10.0.0.8"))
        assertTrue(PrivateAddress.isPrivate("172.16.0.1"))
        assertTrue(PrivateAddress.isPrivate("172.31.255.4"))
        assertTrue(PrivateAddress.isPrivate("192.168.1.42"))
        assertTrue(PrivateAddress.isPrivate("169.254.3.4"))
    }

    @Test
    fun rejectsPublicAndLoopbackAddresses() {
        assertFalse(PrivateAddress.isPrivate("8.8.8.8"))
        assertFalse(PrivateAddress.isPrivate("1.1.1.1"))
        assertFalse(PrivateAddress.isPrivate("172.15.0.1"))
        assertFalse(PrivateAddress.isPrivate("172.32.0.1"))
        assertFalse(PrivateAddress.isPrivate("127.0.0.1"))
        assertFalse(PrivateAddress.isPrivate("firetv.local"))
        assertFalse(PrivateAddress.isPrivate(""))
        assertNull(PrivateAddress.normalize("https://example.com/remote"))
        assertEquals("192.168.0.20", PrivateAddress.normalize("http://192.168.0.20:8080/v1"))
    }

    @Test
    fun commandRequestStaysOnTheLan() {
        val request = buildCommandRequest("192.168.1.20", "Ab12CdE", RemoteActions.VOLUME_UP)
        assertEquals("https://192.168.1.20:8080/v1/FireTV?action=volume_up", request.url.toString())
        assertEquals("0987654321", request.header("X-Api-Key"))
        assertEquals("Ab12CdE", request.header("X-Client-Token"))
        assertEquals("""{"keyActionType":"keyDownUp"}""", request.body!!.contentLength().let {
            val buffer = okio.Buffer()
            request.body!!.writeTo(buffer)
            buffer.readUtf8()
        })
        assertEquals("http://192.168.1.20:8009/apps/FireTVRemote", buildWakeRequest("192.168.1.20").url.toString())
    }

    @Test
    fun publicHostIsRefused() {
        try {
            buildCommandRequest("8.8.8.8", "token", RemoteActions.UP)
            throw AssertionError("public address was allowed")
        } catch (_: IllegalArgumentException) {
        }
    }

    @Test
    fun readsFireTvDescriptionAndPinToken() {
        val description = parseDialDescription(
            """
            <root>
              <device>
                <friendlyName>Living Room</friendlyName>
                <manufacturer>Amazon</manufacturer>
                <modelName>AFTMM</modelName>
              </device>
            </root>
            """.trimIndent(),
        )
        assertTrue(isFireTv(description))
        assertEquals("Living Room", description.friendlyName)
        assertFalse(
            isFireTv(
                parseDialDescription("<manufacturer>Google Inc.</manufacturer><modelName>Chromecast</modelName>"),
            ),
        )
        assertEquals(
            "http://192.168.1.20:8008/description.xml",
            locationFromSsdp("HTTP/1.1 200 OK\r\nLOCATION: http://192.168.1.20:8008/description.xml\r\nST: dial\r\n"),
        )
        assertEquals("192.168.1.20", hostFromHttpUrl("http://192.168.1.20:8008/description.xml"))
        assertNull(hostFromHttpUrl("http://8.8.8.8/description.xml"))
        assertEquals("Wj9PP8I", tokenFromPinResponse("""{"description":"Wj9PP8I"}""", null))
        assertNull(tokenFromPinResponse("""{"description":"Unauthorized"}""", null))
    }
}
