package com.nuvio.tv.core.companion

import java.net.URI
import java.security.SecureRandom
import java.util.UUID

/** Version 1 intentionally accepts only media URLs that the TV can fetch itself. */
data class CompanionPlayRequest(
    val requestId: String = "",
    val url: String = "",
    val title: String = "",
    val headers: Map<String, String> = emptyMap(),
    val contentId: String? = null,
    val contentType: String? = null,
    val videoId: String? = null,
    val season: Int? = null,
    val episode: Int? = null,
    val episodeTitle: String? = null,
    val poster: String? = null,
    val backdrop: String? = null,
    val logo: String? = null,
    val streamName: String? = null,
    val addonName: String? = null,
    val filename: String? = null,
    val bingeGroup: String? = null,
    val subtitles: List<CompanionSubtitle> = emptyList()
) {
    fun validate() {
        require(runCatching { UUID.fromString(requestId).toString() == requestId }.getOrDefault(false))
        require(isRemoteMediaUrl(url)) { "This stream cannot be played directly on TV." }
        require(title.isNotBlank() && title.length <= 2048)
        require(headers.size <= 32 && headers.all { (key, value) ->
            key.matches(Regex("[!#$%&'*+.^_`|~0-9A-Za-z-]+")) &&
                value.length <= 8192 && !value.contains('\r') && !value.contains('\n')
        })
        require(subtitles.size <= 64 && subtitles.all { subtitle ->
            isRemoteMediaUrl(subtitle.url) && subtitle.headers.size <= 32 &&
                subtitle.headers.all { (key, value) ->
                    key.matches(Regex("[!#$%&'*+.^_`|~0-9A-Za-z-]+")) &&
                        value.length <= 8192 && !value.contains('\r') && !value.contains('\n')
                }
        })
    }
}

data class CompanionSubtitle(
    val url: String = "",
    val language: String = "und",
    val name: String? = null,
    val headers: Map<String, String> = emptyMap()
)

internal fun isRemoteMediaUrl(value: String): Boolean = runCatching {
    val uri = URI(value)
    val host = uri.host?.removeSurrounding("[", "]")?.lowercase() ?: return false
    uri.scheme?.lowercase() in setOf("http", "https") && uri.userInfo == null &&
        host != "localhost" && !host.endsWith(".localhost") && host != "::1" &&
        host != "0.0.0.0" && !host.startsWith("127.") && !host.startsWith("::ffff:127.")
}.getOrDefault(false)

/** One challenge at a time, expiring and attempt-limited. The code never goes over the API. */
internal class CompanionPairing(private val now: () -> Long = System::currentTimeMillis) {
    data class Challenge(val id: String, val code: String, val expiresAt: Long, var attempts: Int = 0)
    private var challenge: Challenge? = null
    private var nextAllowedStart = 0L

    @Synchronized fun start(): Challenge? {
        if (now() < nextAllowedStart) return null
        if (challenge?.let { now() < it.expiresAt } == true) return null
        return Challenge(UUID.randomUUID().toString(), "%06d".format(java.util.Locale.US, SecureRandom().nextInt(1_000_000)), now() + 120_000).also {
            challenge = it
            nextAllowedStart = now() + 5_000
        }
    }

    @Synchronized fun verify(id: String, code: String): Boolean {
        val current = challenge ?: return false
        if (now() >= current.expiresAt || current.attempts >= 5) { challenge = null; return false }
        if (id != current.id) return false
        current.attempts++
        if (code != current.code) {
            if (current.attempts >= 5) challenge = null
            return false
        }
        challenge = null
        return true
    }

    @Synchronized fun cancel(id: String) { if (challenge?.id == id) challenge = null }
}

/** Player reports are correlated by request ID, never by the previously playing video. */
object CompanionPlaybackStatus {
    private data class Entry(val owner: String, var state: String, val createdAt: Long)
    private val entries = linkedMapOf<String, Entry>()

    @Synchronized fun accept(id: String, owner: String): Boolean {
        entries.entries.removeAll { System.currentTimeMillis() - it.value.createdAt > 300_000 }
        if (entries.containsKey(id)) {
            require(entries[id]?.owner == owner)
            return false
        }
        while (entries.size >= 32) entries.remove(entries.keys.first())
        entries[id] = Entry(owner, "starting", System.currentTimeMillis())
        return true
    }
    @Synchronized fun report(id: String?, state: String) {
        val entry = entries[id] ?: return
        if (entry.state == "starting") entry.state = state
    }
    @Synchronized fun state(id: String, owner: String): String? = entries[id]?.takeIf { it.owner == owner }?.state
}
