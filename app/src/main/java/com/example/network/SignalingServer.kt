package com.example.network

import android.util.Log
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import java.util.Collections
import kotlin.time.Duration.Companion.seconds

class SignalingServer(
    private val port: Int = 8080,
    private val onMessageReceived: (SignalingMessage) -> Unit,
    private val onClientConnected: () -> Unit,
    private val onClientDisconnected: () -> Unit
) {
    private var server: io.ktor.server.engine.EmbeddedServer<*, *>? = null
    private val sessions = Collections.synchronizedSet(LinkedHashSet<DefaultWebSocketServerSession>())
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun start() {
        if (server != null) return
        scope.launch {
            try {
                server = embeddedServer(Netty, port = port) {
                    install(WebSockets) {
                        pingPeriod = 15.seconds
                        timeout = 15.seconds
                        maxFrameSize = Long.MAX_VALUE
                        masking = false
                    }
                    routing {
                        webSocket("/signaling") {
                            sessions.add(this)
                            Log.d("SignalingServer", "Receiver connected to signaling")
                            onClientConnected()

                            try {
                                for (frame in incoming) {
                                    if (frame is Frame.Text) {
                                        val text = frame.readText()
                                        Log.d("SignalingServer", "Received: $text")
                                        val message = SignalingMessage.fromJson(text)
                                        if (message != null) {
                                            onMessageReceived(message)
                                        }
                                    }
                                }
                            } catch (e: Exception) {
                                Log.e("SignalingServer", "Session exception", e)
                            } finally {
                                sessions.remove(this)
                                Log.d("SignalingServer", "Receiver disconnected")
                                onClientDisconnected()
                            }
                        }
                    }
                }
                server?.start(wait = true)
            } catch (e: Exception) {
                Log.e("SignalingServer", "Could not start server on port $port", e)
            }
        }
    }

    fun stop() {
        scope.launch {
            try {
                sessions.forEach {
                    try {
                        it.close(CloseReason(CloseReason.Codes.NORMAL, "Server stopping"))
                    } catch (ignored: Exception) {}
                }
                sessions.clear()
                server?.stop(1000, 2000)
                server = null
            } catch (e: Exception) {
                Log.e("SignalingServer", "Error stopping Ktor signaling server", e)
            }
        }
    }

    fun sendMessage(message: SignalingMessage) {
        val json = SignalingMessage.toJson(message)
        scope.launch {
            val targets = sessions.toList()
            for (session in targets) {
                try {
                    session.send(Frame.Text(json))
                } catch (e: Exception) {
                    Log.e("SignalingServer", "Failed to send message to receiver session", e)
                }
            }
        }
    }
}
