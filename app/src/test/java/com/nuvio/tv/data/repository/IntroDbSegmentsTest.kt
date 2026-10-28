package com.nuvio.tv.data.repository

import com.nuvio.tv.data.remote.api.IntroDbSegmentsResponse
import com.nuvio.tv.data.remote.dto.trakt.TraktDeviceTokenRequestDto
import com.squareup.moshi.Moshi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class IntroDbSegmentsTest {
    private val moshi = Moshi.Builder().build()

    @Test
    fun `public IntroDB response includes recap starting at zero and intro in seconds`() {
        val response = moshi.adapter(IntroDbSegmentsResponse::class.java).fromJson("""
            {"imdb_id":"tt1266020","season":3,"episode":1,
             "intro":{"start_sec":147,"end_sec":168,"start_ms":147000,"end_ms":168000},
             "recap":{"start_sec":0,"end_sec":72},"outro":null,
             "no_intro":false,"no_recap":false,"no_outro":false}
        """.trimIndent())!!
        val segments = response.toSkipIntervals(movie = false)
        assertEquals(SkipInterval(0.0, 72.0, "recap", "introdb"), segments.single { it.type == "recap" })
        assertEquals(147.0, segments.single { it.type == "intro" }.startTime, 0.0)
    }

    @Test
    fun `device token requests omit deprecated client secret`() {
        val body = moshi.adapter(TraktDeviceTokenRequestDto::class.java)
            .toJson(TraktDeviceTokenRequestDto(code = "device-test", clientId = "public-client"))
        assertFalse(body.contains("client_secret"))
    }
}
