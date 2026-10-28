package com.nuvio.tv.core.companion

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.nuvio.tv.R
import fi.iki.elonen.NanoHTTPD
import kotlinx.coroutines.flow.MutableStateFlow
import java.util.UUID

/** Runs only while the authenticated/profile-selected TV UI is in the foreground. */
class CompanionReceiver(private val activity: Activity) {
    val pendingPlay = MutableStateFlow<CompanionPlayRequest?>(null)
    private val prefs = activity.getSharedPreferences("companion", Context.MODE_PRIVATE)
    private val handler = Handler(Looper.getMainLooper())
    private val nsd = activity.getSystemService(Context.NSD_SERVICE) as NsdManager
    private var server: CompanionHttpServer? = null
    private var registration: NsdManager.RegistrationListener? = null
    private var dialog: AlertDialog? = null
    private var ready = false
    private var foreground = false
    private val deviceId = prefs.getString("deviceId", null) ?: UUID.randomUUID().toString().also {
        prefs.edit().putString("deviceId", it).apply()
    }

    fun setReady(value: Boolean) { ready = value; reconcile() }
    fun setForeground(value: Boolean) { foreground = value; reconcile() }

    private fun reconcile() {
        if (ready && foreground && server == null) {
            lateinit var candidate: CompanionHttpServer
            candidate = CompanionHttpServer(
                deviceId = deviceId,
                deviceName = "Nuvio · ${Build.MODEL}",
                isTokenValid = { token -> prefs.getStringSet("tokens", emptySet()).orEmpty().contains(token) },
                saveToken = { token ->
                    synchronized(prefs) {
                        val tokens = prefs.getStringSet("tokens", emptySet()).orEmpty().toMutableSet()
                        if (tokens.size >= 16) tokens.remove(tokens.first())
                        tokens.add(token)
                        check(prefs.edit().putStringSet("tokens", tokens).commit())
                    }
                },
                onChallenge = { challenge -> handler.post {
                    if (server !== candidate || !ready || !foreground) return@post
                    dialog?.dismiss()
                    val shown = AlertDialog.Builder(activity)
                        .setTitle(R.string.companion_pair_title)
                        .setMessage(activity.getString(R.string.companion_pair_message, challenge.code))
                        .setNegativeButton(android.R.string.cancel) { _, _ -> candidate.cancelPairing(challenge.id) }
                        .setOnCancelListener { candidate.cancelPairing(challenge.id) }
                        .show()
                    dialog = shown
                    handler.postDelayed({
                        if (dialog === shown) { shown.dismiss(); dialog = null }
                        candidate.cancelPairing(challenge.id)
                    }, 120_000)
                } },
                onPaired = { handler.post {
                    if (server === candidate) { dialog?.dismiss(); dialog = null }
                } },
                onPlay = { request -> handler.post {
                    if (server === candidate && ready && foreground) {
                        pendingPlay.value?.let { CompanionPlaybackStatus.report(it.requestId, "stopped") }
                        pendingPlay.value = request
                    } else CompanionPlaybackStatus.report(request.requestId, "stopped")
                } }
            )
            try {
                candidate.start(NanoHTTPD.SOCKET_READ_TIMEOUT, true)
                server = candidate
                val listener = object : NsdManager.RegistrationListener {
                    override fun onServiceRegistered(info: NsdServiceInfo) = Unit
                    override fun onRegistrationFailed(info: NsdServiceInfo, code: Int) { Log.w("Companion", "Discovery registration failed: $code") }
                    override fun onServiceUnregistered(info: NsdServiceInfo) = Unit
                    override fun onUnregistrationFailed(info: NsdServiceInfo, code: Int) = Unit
                }
                registration = listener
                nsd.registerService(NsdServiceInfo().apply {
                    serviceName = "Nuvio · ${Build.MODEL}"
                    serviceType = "_nuvio-tv._tcp."
                    port = candidate.listeningPort
                    setAttribute("id", deviceId)
                    setAttribute("version", "1")
                }, NsdManager.PROTOCOL_DNS_SD, listener)
            } catch (e: Exception) {
                Log.w("Companion", "Receiver could not start", e)
                candidate.stop()
                server = null
            }
        } else if (!ready || !foreground) {
            registration?.let { runCatching { nsd.unregisterService(it) } }
            registration = null
            server?.stop()
            server = null
            dialog?.dismiss()
            dialog = null
            pendingPlay.value?.let { CompanionPlaybackStatus.report(it.requestId, "stopped") }
            pendingPlay.value = null
        }
    }

}
