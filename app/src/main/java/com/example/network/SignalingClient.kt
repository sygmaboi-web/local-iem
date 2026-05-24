package com.example.network

import android.util.Log
import okhttp3.*

class SignalingClient(
    private val hostAddress: String,
    private val port: Int = 8080,
    private val onMessageReceived: (SignalingMessage) -> Unit,
    private val onConnected: () -> Unit,
    private val onDisconnected: () -> Unit
) {
    private var client: OkHttpClient? = null
    private var webSocket: WebSocket? = null

    fun connect() {
        if (webSocket != null) return
        client = OkHttpClient.Builder().build()
        val url = "ws://$hostAddress:$port/signaling"
        Log.d("SignalingClient", "Connecting to signaling server at: $url")
        val request = Request.Builder()
            .url(url)
            .build()

        webSocket = client?.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d("SignalingClient", "Connected to signaling server")
                onConnected()
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                Log.d("SignalingClient", "Received signaling message: $text")
                val message = SignalingMessage.fromJson(text)
                if (message != null) {
                    onMessageReceived(message)
                }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d("SignalingClient", "Connection closed: $reason ($code)")
                onDisconnected()
                this@SignalingClient.webSocket = null
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e("SignalingClient", "WebSocket failure", t)
                onDisconnected()
                this@SignalingClient.webSocket = null
            }
        })
    }

    fun disconnect() {
        try {
            webSocket?.close(1000, "Disconnecting")
        } catch (ignored: Exception) {}
        webSocket = null
        client = null
    }

    fun sendMessage(message: SignalingMessage) {
        val json = SignalingMessage.toJson(message)
        val success = webSocket?.send(json) ?: false
        if (!success) {
            Log.e("SignalingClient", "Failed to send message: socket not ready")
        }
    }
}
