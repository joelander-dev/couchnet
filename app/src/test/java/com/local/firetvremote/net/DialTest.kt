package com.local.firetvremote.net

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DialTest {
    @Test
    fun readsNamespacedTagsWithAttributes() {
        val description = parseDialDescription(
            """<root xmlns:d="urn:x"><d:friendlyName lang="en">  Den  </d:friendlyName></root>""",
        )
        assertEquals("Den", description.friendlyName)
    }

    @Test
    fun decodesXmlEntitiesInNames() {
        val description = parseDialDescription(
            "<friendlyName>Tom &amp; Jerry&apos;s &quot;TV&quot;</friendlyName>",
        )
        assertEquals("Tom & Jerry's \"TV\"", description.friendlyName)
    }

    @Test
    fun missingOrBlankTagsAreNull() {
        val missing = parseDialDescription("<root/>")
        assertNull(missing.friendlyName)
        assertNull(missing.manufacturer)
        assertNull(missing.modelName)
        assertNull(missing.deviceType)
        assertNull(parseDialDescription("<friendlyName>   </friendlyName>").friendlyName)
    }

    @Test
    fun recognisesFireTvByModelOrManufacturer() {
        assertTrue(isFireTv(DialDescription(null, null, "aftka", null)))
        assertTrue(isFireTv(DialDescription(null, "Amazon.com Services LLC", "X", null)))
        assertTrue(isFireTv(DialDescription(null, " amz ", null, null)))
    }

    @Test
    fun rejectsOtherStreamingDevices() {
        assertFalse(isFireTv(DialDescription("Den", "Roku", "Roku Ultra", null)))
        assertFalse(isFireTv(DialDescription("Office", "Samsung Electronics", "QN65", null)))
        assertFalse(isFireTv(DialDescription(null, null, null, null)))
    }

    @Test
    fun genericDialDevicesAreNotFireTvs() {
        val chromecast = parseDialDescription(
            """
            <device>
              <deviceType>urn:dial-multiscreen-org:device:dial:1</deviceType>
              <friendlyName>Kitchen</friendlyName>
              <manufacturer>Google Inc.</manufacturer>
              <modelName>Eureka Dongle</modelName>
            </device>
            """.trimIndent(),
        )
        assertFalse(isFireTv(chromecast))
    }

    @Test
    fun netflixMarkerAloneIsNotAFireTv() {
        val smartTv = "HTTP/1.1 200 OK\r\nSERVER: Linux/4.4 UPnP/1.0\r\n" +
            "X-User-Agent: NRDP MDX\r\nUSN: uuid:lg-webos::urn:dial-multiscreen-org:service:dial:1\r\n"
        assertFalse(ssdpLooksLikeFireTv(smartTv))
        assertTrue(ssdpLooksLikeFireTv("SERVER: Linux UPnP/1.0 AFTT/1\r\n"))
    }

    @Test
    fun prefersHeaderTokenOverBody() {
        assertEquals("HeadTok1", tokenFromPinResponse("""{"description":"BodyTok1"}""", " HeadTok1 "))
        assertEquals("BodyTok1", tokenFromPinResponse("""{"description":"BodyTok1"}""", "invalid"))
        assertEquals("Tok_12-3", tokenFromPinResponse("""{ "description" : "Tok_12-3" }""", null))
    }

    @Test
    fun rejectsThingsThatAreNotTokens() {
        assertNull(tokenFromPinResponse(null, null))
        assertNull(tokenFromPinResponse("  ", ""))
        assertNull(tokenFromPinResponse("""{"description":"abc"}""", null))
        assertNull(tokenFromPinResponse("""{"description":"ab${'$'}cd"}""", null))
        assertNull(tokenFromPinResponse("""{"description":"${"a".repeat(81)}"}""", null))
        assertNull(tokenFromPinResponse("""{"description":"PinError"}""", null))
        assertNull(tokenFromPinResponse("""{"status":"ok"}""", null))
    }

    @Test
    fun readsSsdpHeadersCaseInsensitively() {
        val reply = "HTTP/1.1 200 OK\r\nlocation: http://192.168.1.5:8008/dd.xml\r\nST: dial\r\n"
        assertEquals("http://192.168.1.5:8008/dd.xml", ssdpHeader(reply, "LOCATION"))
        assertEquals("dial", ssdpHeader(reply, "st"))
        assertNull(ssdpHeader(reply, "USN"))
        assertNull(ssdpHeader("LOCATION:   \r\n", "LOCATION"))
    }

    @Test
    fun decodesBase64NamesOnlyWhenPrintable() {
        assertEquals("Living Room", decodeAdvertisedName("  TGl2aW5nIFJvb20=  "))
        // Decodes to control bytes, so the raw text is kept.
        assertEquals("AAECAw==", decodeAdvertisedName("AAECAw=="))
        assertEquals("Den", decodeAdvertisedName("Den"))
    }

    @Test
    fun descriptionUrlsMustBeLocal() {
        assertEquals("10.0.0.5", hostFromHttpUrl("https://10.0.0.5/x"))
        assertNull(hostFromHttpUrl("http://firetv.local:8008/description.xml"))
        assertNull(hostFromHttpUrl("http://203.0.113.9:8008/description.xml"))
    }
}
