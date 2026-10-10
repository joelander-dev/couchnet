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
        assertEquals(
            "https://192.168.1.20:8080/v1/FireTV?action=channel_up",
            buildCommandRequest("192.168.1.20", "Ab12CdE", RemoteActions.CHANNEL_UP).url.toString(),
        )
        assertEquals(
            "https://192.168.1.20:8080/v1/FireTV?action=channel_down",
            buildCommandRequest("192.168.1.20", "Ab12CdE", RemoteActions.CHANNEL_DOWN).url.toString(),
        )
        assertEquals(
            "https://192.168.1.20:8080/v1/FireTV?action=menu",
            buildCommandRequest("192.168.1.20", "Ab12CdE", RemoteActions.OPTIONS).url.toString(),
        )
        assertEquals(
            "https://192.168.1.20:8080/v1/FireTV?action=power",
            buildCommandRequest("192.168.1.20", "Ab12CdE", RemoteActions.POWER).url.toString(),
        )
        assertFalse(RemoteActions.POWER in RemoteActions.repeating)
    }

    @Test
    fun mediaButtonsUseTheMediaEndpoint() {
        val play = buildMediaRequest("192.168.1.20", "Ab12CdE", RemoteActions.PLAY)
        assertEquals("https://192.168.1.20:8080/v1/media?action=play", play.url.toString())
        assertEquals("0987654321", play.header("X-Api-Key"))
        assertEquals("Ab12CdE", play.header("X-Client-Token"))
        assertEquals("okhttp/4.10.0", play.header("User-Agent"))
        assertEquals("{}", bodyText(play))

        val pause = buildMediaRequest("192.168.1.20", "Ab12CdE", RemoteActions.PAUSE)
        assertEquals("https://192.168.1.20:8080/v1/media?action=pause", pause.url.toString())
        assertEquals("{}", bodyText(pause))

        val forward = buildMediaRequest("192.168.1.20", "Ab12CdE", RemoteActions.FAST_FORWARD)
        assertEquals("https://192.168.1.20:8080/v1/media?action=scan", forward.url.toString())
        assertEquals(
            """{"direction":"forward","keyAction":{"keyActionType":"keyDown"}}""",
            bodyText(forward),
        )

        val rewind = buildMediaRequest("192.168.1.20", "Ab12CdE", RemoteActions.REWIND)
        assertEquals("https://192.168.1.20:8080/v1/media?action=scan", rewind.url.toString())
        assertEquals(
            """{"direction":"back","keyAction":{"keyActionType":"keyDown"}}""",
            bodyText(rewind),
        )
        assertFalse(play.url.toString().contains("/v1/FireTV"))
        assertEquals("application/json; charset=utf-8", play.body!!.contentType().toString())
        assertTrue(RemoteActions.FAST_FORWARD in RemoteActions.repeating)
        assertTrue(RemoteActions.REWIND in RemoteActions.repeating)
        assertFalse(RemoteActions.PLAY in RemoteActions.repeating)
        assertFalse(RemoteActions.PAUSE in RemoteActions.repeating)
    }

    @Test
    fun publicHostIsRefused() {
        try {
            buildCommandRequest("8.8.8.8", "token", RemoteActions.UP)
            throw AssertionError("public address was allowed")
        } catch (_: IllegalArgumentException) {
        }
        try {
            buildMediaRequest("8.8.8.8", "token", RemoteActions.PLAY)
            throw AssertionError("public address was allowed for media")
        } catch (_: IllegalArgumentException) {
        }
    }

    private fun bodyText(request: okhttp3.Request): String {
        val buffer = okio.Buffer()
        request.body!!.writeTo(buffer)
        return buffer.readUtf8()
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
        assertEquals(
            "Bedroom",
            parseDialDescription("<friendlyName><![CDATA[Bedroom]]></friendlyName>").friendlyName,
        )
        assertTrue(isGeneratedTvName("Possible Fire TV"))
        assertFalse(isGeneratedTvName("Living Room"))
        assertEquals(
            "https://192.168.1.20:8080/v1/FireTV?action=home",
            buildCommandRequest("192.168.1.20", "Ab12CdE", RemoteActions.HOME).url.toString(),
        )
        assertFalse(
            isFireTv(
                parseDialDescription("<manufacturer>Google Inc.</manufacturer><modelName>Chromecast</modelName>"),
            ),
        )
        val edition = parseDialDescription(
            """
            <device>
              <deviceType>urn:schemas-upnp-org:device:tvdevice:1</deviceType>
              <friendlyName>Living Room</friendlyName>
              <manufacturer>AMZ</manufacturer>
              <modelName>AFTMM</modelName>
            </device>
            """.trimIndent(),
        )
        assertTrue(isFireTv(edition))
        assertEquals("Living Room", edition.friendlyName)
        assertEquals("Living Room", decodeAdvertisedName("TGl2aW5nIFJvb20="))
        assertEquals("Living Room", decodeAdvertisedName("Living Room"))
        assertTrue(
            ssdpLooksLikeFireTv(
                "USN: uuid:amazon-fire-tv::upnp:rootdevice\r\nX-User-Agent: NRDP MDX\r\n",
            ),
        )
        assertFalse(ssdpLooksLikeFireTv("USN: uuid:printer::upnp:rootdevice\r\n"))
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
