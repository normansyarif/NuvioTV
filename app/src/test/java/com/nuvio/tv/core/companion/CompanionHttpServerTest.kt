package com.nuvio.tv.core.companion

import com.google.gson.Gson
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaType
import java.util.UUID

/** Real HTTP requests exercise framing, authorization and the iOS/TV wire contract. */
class CompanionHttpServerTest {
    private val gson = Gson()
    private lateinit var server: CompanionHttpServer
    private var challenge: CompanionPairing.Challenge? = null
    private val tokens = mutableSetOf("existing-phone", "another-phone")
    private val launches = mutableListOf<CompanionPlayRequest>()

    @Before fun start() {
        server = CompanionHttpServer("test-tv", "Living room", { it in tokens }, { tokens.add(it) },
            { challenge = it }, {}, { launches.add(it) })
        server.start(5_000, true)
    }
    @After fun stop() { server.stop() }

    private fun call(path: String, body: String? = null, token: String? = null, origin: String? = null): Pair<Int, String> {
        val request = Request.Builder().url("http://127.0.0.1:${server.listeningPort}/v1/$path")
        token?.let { request.header("Authorization", "Bearer $it") }
        origin?.let { request.header("Origin", it) }
        if (body != null) request.post(body.toRequestBody("application/json; charset=utf-8".toMediaType()))
        return OkHttpClient().newCall(request.build()).execute().use { response ->
            response.code to response.body!!.string()
        }
    }

    @Test fun `pairing reveals challenge ID but never code and requires correct code`() {
        val started = call("pair/start", "{}")
        assertEquals(200, started.first)
        assertNotNull(challenge)
        assertFalse(started.second.contains(challenge!!.code))
        assertFalse(started.second.contains("\"code\""))
        assertEquals(403, call("pair", gson.toJson(mapOf("challengeId" to challenge!!.id, "code" to "wrong"))).first)
        val paired = call("pair", gson.toJson(mapOf("challengeId" to challenge!!.id, "code" to challenge!!.code)))
        assertEquals(200, paired.first)
        val token = gson.fromJson(paired.second, Map::class.java)["token"] as String
        assertTrue(token in tokens)
        assertEquals(403, call("pair", gson.toJson(mapOf("challengeId" to challenge!!.id, "code" to challenge!!.code))).first)
    }

    @Test fun `play is authenticated idempotent and waits for real player confirmation`() {
        val request = CompanionPlayRequest(UUID.randomUUID().toString(), "https://example.org/v?a=x%2By", "Title", mapOf("Referer" to "https://example.org/"))
        val body = gson.toJson(request)
        assertEquals(401, call("play", body).first)
        assertEquals(401, call("play", body, "wrong-token").first)
        assertTrue(launches.isEmpty())
        assertEquals(202, call("play", body, "existing-phone").first)
        assertEquals(202, call("play", body, "existing-phone").first)
        assertEquals(listOf(request), launches)
        assertTrue(call("play/${request.requestId}", token = "existing-phone").second.contains("starting"))
        assertEquals(404, call("play/${request.requestId}", token = "another-phone").first)
        CompanionPlaybackStatus.report(request.requestId, "playing")
        assertTrue(call("play/${request.requestId}", token = "existing-phone").second.contains("playing"))
    }

    @Test fun `rejects browser posts malformed JSON localhost and oversized requests`() {
        assertEquals(403, call("pair/start", "{}", origin = "https://example.org").first)
        assertEquals(400, call("play", "{broken", "existing-phone").first)
        val local = CompanionPlayRequest(UUID.randomUUID().toString(), "http://127.0.0.1/video", "Title")
        assertEquals(400, call("play", gson.toJson(local), "existing-phone").first)
        assertEquals(400, call("play", "x".repeat(65537), "existing-phone").first)
        assertTrue(launches.isEmpty())
    }
}
