package com.nuvio.tv.core.companion

import com.google.gson.Gson
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class CompanionProtocolTest {
    @Test fun `pairing expires and cannot be replayed`() {
        var now = 1_000L
        val pairing = CompanionPairing { now }
        val challenge = pairing.start()!!
        assertNull(pairing.start())
        assertFalse(pairing.verify("another-device", challenge.code))
        assertTrue(pairing.verify(challenge.id, challenge.code))
        assertFalse(pairing.verify(challenge.id, challenge.code))
        now += 5_001
        val expired = pairing.start()!!
        now += 120_000
        assertFalse(pairing.verify(expired.id, expired.code))
    }

    @Test fun `five incorrect codes invalidate pairing`() {
        val pairing = CompanionPairing()
        val challenge = pairing.start()!!
        val wrong = if (challenge.code == "000000") "111111" else "000000"
        repeat(5) { assertFalse(pairing.verify(challenge.id, wrong)) }
        assertFalse(pairing.verify(challenge.id, challenge.code))
    }

    @Test fun `TV cancel invalidates challenge`() {
        val pairing = CompanionPairing()
        val challenge = pairing.start()!!
        pairing.cancel(challenge.id)
        assertFalse(pairing.verify(challenge.id, challenge.code))
    }

    @Test fun `playback URL preserves signed query parameters and headers`() {
        val request = CompanionPlayRequest(
            requestId = UUID.randomUUID().toString(),
            url = "https://example.org/movie.mkv?token=a%2Bb%25&name=hello+world",
            title = "Episode 1", headers = mapOf("Referer" to "https://example.org/", "Authorization" to "Bearer abc"),
            contentId = "tt1266020", videoId = "tt1266020:3:1", season = 3, episode = 1,
            subtitles = listOf(CompanionSubtitle("https://example.org/sub.vtt", "en"))
        )
        val decoded = Gson().fromJson(Gson().toJson(request), CompanionPlayRequest::class.java)
        decoded.validate()
        assertEquals(request, decoded)
    }

    @Test fun `rejects local device URLs nonmedia schemes and header injection`() {
        listOf("http://localhost:8080/video", "http://127.0.0.1/video", "http://[::1]/video", "file:///video", "magnet:?xt=abc", "https://user:pass@example.org/video").forEach {
            assertFalse(it, isRemoteMediaUrl(it))
        }
        assertTrue(isRemoteMediaUrl("http://192.168.1.5/video"))
        val request = CompanionPlayRequest(UUID.randomUUID().toString(), "https://example.org/v", "Title", mapOf("Referer" to "ok\r\nInjected: yes"))
        assertThrows(IllegalArgumentException::class.java) { request.validate() }
        assertThrows(IllegalArgumentException::class.java) { request.copy(headers = emptyMap(), requestId = "bad-id").validate() }
    }

    @Test fun `retries do not replay playback and status is scoped to paired device`() {
        val id = UUID.randomUUID().toString()
        assertTrue(CompanionPlaybackStatus.accept(id, "phone1"))
        assertFalse(CompanionPlaybackStatus.accept(id, "phone1"))
        assertEquals("starting", CompanionPlaybackStatus.state(id, "phone1"))
        assertNull(CompanionPlaybackStatus.state(id, "phone2"))
        assertThrows(IllegalArgumentException::class.java) { CompanionPlaybackStatus.accept(id, "phone2") }
        CompanionPlaybackStatus.report(UUID.randomUUID().toString(), "playing")
        assertEquals("starting", CompanionPlaybackStatus.state(id, "phone1"))
        CompanionPlaybackStatus.report(id, "playing")
        CompanionPlaybackStatus.report(id, "stopped")
        assertEquals("playing", CompanionPlaybackStatus.state(id, "phone1"))
    }
}
