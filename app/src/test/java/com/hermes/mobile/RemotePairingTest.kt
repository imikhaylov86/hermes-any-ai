package com.hermes.mobile

import com.hermes.mobile.transport.auth.RemotePairingHelper
import org.junit.Assert.*
import org.junit.Test

class RemotePairingTest {

    @Test
    fun testParseDirectUrl() {
        val parsed = RemotePairingHelper.parsePairingString("https://my-server.com/api/ws")
        assertNotNull(parsed)
        assertEquals("wss://my-server.com/api/ws", parsed!!.serverUrl)
    }

    @Test
    fun testParseJsonConfig() {
        val json = """
            {
                "url": "http://192.168.1.50:8000/api/ws",
                "token": "tok_12345",
                "name": "Homelab Mac"
            }
        """.trimIndent()

        val parsed = RemotePairingHelper.parsePairingString(json)
        assertNotNull(parsed)
        assertTrue(parsed!!.serverUrl.startsWith("ws://192.168.1.50:8000/api/ws"))
        assertTrue(parsed.serverUrl.contains("token=tok_12345"))
        assertEquals("tok_12345", parsed.token)
        assertEquals("Homelab Mac", parsed.name)
    }

    @Test
    fun testParseHermesUri() {
        val uri = "hermes://connect?url=https%3A%2F%2Fgateway.myvpn.net%2Fws&token=supersecret&name=VPS%20Node"
        val parsed = RemotePairingHelper.parsePairingString(uri)
        assertNotNull(parsed)
        assertTrue(parsed!!.serverUrl.startsWith("wss://gateway.myvpn.net/ws"))
        assertTrue(parsed.serverUrl.contains("token=supersecret"))
        assertEquals("supersecret", parsed.token)
        assertEquals("VPS Node", parsed.name)
    }

    @Test
    fun testInvalidContent() {
        val parsed = RemotePairingHelper.parsePairingString("some random text")
        assertNull(parsed)
    }
}
