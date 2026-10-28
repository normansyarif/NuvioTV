package com.nuvio.tv.core.companion

import com.google.gson.Gson
import fi.iki.elonen.NanoHTTPD
import java.security.SecureRandom

internal class CompanionHttpServer(
    private val deviceId: String,
    private val deviceName: String,
    private val isTokenValid: (String) -> Boolean,
    private val saveToken: (String) -> Unit,
    private val onChallenge: (CompanionPairing.Challenge) -> Unit,
    private val onPaired: () -> Unit,
    private val onPlay: (CompanionPlayRequest) -> Unit
) : NanoHTTPD(0) {
    private val gson = Gson()
    private val pairing = CompanionPairing()
    fun cancelPairing(id: String) = pairing.cancel(id)

    override fun serve(session: IHTTPSession): Response {
        fun response(status: Response.Status, body: Any): Response = newFixedLengthResponse(
            status, "application/json; charset=utf-8", gson.toJson(body)
        ).apply {
            addHeader("Cache-Control", "no-store")
            // Rejected requests may have an unread body. Never let those bytes become
            // another request on the same connection (including unauthorized posts).
            closeConnection(true)
        }
        fun error(status: Response.Status, message: String) = response(status, mapOf("error" to message))
        if (session.headers.containsKey("origin")) return error(Response.Status.FORBIDDEN, "Browser requests are not supported")
        if (session.method == Method.GET && session.uri == "/v1/info") {
            return response(Response.Status.OK, mapOf("id" to deviceId, "version" to 1, "name" to deviceName))
        }
        val token = session.headers["authorization"]?.removePrefix("Bearer ")
        val authorized = token != null && isTokenValid(token)
        if (session.uri !in setOf("/v1/pair/start", "/v1/pair") && !authorized) {
            return error(Response.Status.UNAUTHORIZED, "Pair this iPhone with the TV first")
        }
        if (session.method == Method.GET && session.uri.startsWith("/v1/play/")) {
            val state = CompanionPlaybackStatus.state(session.uri.substringAfterLast('/'), token!!) ?: return error(Response.Status.NOT_FOUND, "Request not found")
            return response(Response.Status.OK, mapOf("state" to state))
        }
        if (session.method != Method.POST) return error(Response.Status.NOT_FOUND, "Not found")
        if (session.headers["content-type"]?.substringBefore(';')?.trim() != "application/json") {
            return error(Response.Status.BAD_REQUEST, "Expected JSON")
        }
        val size = session.headers["content-length"]?.toIntOrNull()
        if (size == null || size !in 2..65536 || session.headers.containsKey("transfer-encoding")) {
            return error(Response.Status.BAD_REQUEST, "Invalid request size")
        }
        return try {
            val bytes = ByteArray(size)
            var offset = 0
            while (offset < size) {
                val count = session.inputStream.read(bytes, offset, size - offset)
                require(count > 0)
                offset += count
            }
            val body = bytes.toString(Charsets.UTF_8)
            when (session.uri) {
                "/v1/pair/start" -> {
                    val challenge = pairing.start() ?: return error(Response.Status.CONFLICT, "A pairing request is already active. Try again shortly.")
                    onChallenge(challenge)
                    response(Response.Status.OK, mapOf("challengeId" to challenge.id))
                }
                "/v1/pair" -> {
                    val data = gson.fromJson(body, Map::class.java)
                    if (!pairing.verify(data["challengeId"] as? String ?: "", data["code"] as? String ?: "")) {
                        return error(Response.Status.FORBIDDEN, "The code is incorrect or expired. Try pairing again.")
                    }
                    val newToken = ByteArray(32).also { SecureRandom().nextBytes(it) }.joinToString("") { "%02x".format(it) }
                    saveToken(newToken)
                    onPaired()
                    response(Response.Status.OK, mapOf("token" to newToken))
                }
                "/v1/play" -> {
                    val request = gson.fromJson(body, CompanionPlayRequest::class.java)
                    request.validate()
                    if (CompanionPlaybackStatus.accept(request.requestId, token!!)) {
                        onPlay(request)
                    }
                    response(Response.Status.ACCEPTED, mapOf("requestId" to request.requestId))
                }
                else -> error(Response.Status.NOT_FOUND, "Not found")
            }
        } catch (_: Exception) { error(Response.Status.BAD_REQUEST, "Invalid playback request or unsupported stream") }
    }
}
