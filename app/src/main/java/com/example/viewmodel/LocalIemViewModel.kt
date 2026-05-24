package com.example.viewmodel

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.audio.LocalAudioPlayer
import com.example.network.NsdHelper
import com.example.network.SignalingClient
import com.example.network.SignalingMessage
import com.example.network.SignalingServer
import com.example.webrtc.WebRtcManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.webrtc.IceCandidate
import org.webrtc.SessionDescription

class LocalIemViewModel(application: Application) : AndroidViewModel(application) {

    private val context = application.applicationContext

    // App Roles: "None", "Broadcaster", "Receiver"
    private val _currentRole = MutableStateFlow("None")
    val currentRole: StateFlow<String> = _currentRole.asStateFlow()

    // --- Broadcaster States ---
    private val _isBroadcasting = MutableStateFlow(false)
    val isBroadcasting: StateFlow<Boolean> = _isBroadcasting.asStateFlow()

    private val _broadcasterIp = MutableStateFlow("")
    val broadcasterIp: StateFlow<String> = _broadcasterIp.asStateFlow()

    private val _isReceiverConnected = MutableStateFlow(false)
    val isReceiverConnected: StateFlow<Boolean> = _isReceiverConnected.asStateFlow()

    private val _monitorLocalEnabled = MutableStateFlow(false)
    val monitorLocalEnabled: StateFlow<Boolean> = _monitorLocalEnabled.asStateFlow()

    // --- Receiver States ---
    private val _isReceiving = MutableStateFlow(false)
    val isReceiving: StateFlow<Boolean> = _isReceiving.asStateFlow()

    private val _targetBroadcasterIp = MutableStateFlow("")
    val targetBroadcasterIp: StateFlow<String> = _targetBroadcasterIp.asStateFlow()

    private val _isConnectingToBroadcaster = MutableStateFlow(false)
    val isConnectingToBroadcaster: StateFlow<Boolean> = _isConnectingToBroadcaster.asStateFlow()

    private val _receiverVolume = MutableStateFlow(1.0f)
    val receiverVolume: StateFlow<Float> = _receiverVolume.asStateFlow()

    // --- Audio Player & Waveform States ---
    private val _audioAmplitude = MutableStateFlow(0f)
    val audioAmplitude: StateFlow<Float> = _audioAmplitude.asStateFlow()

    private val _playbackTrackName = MutableStateFlow("")
    val playbackTrackName: StateFlow<String> = _playbackTrackName.asStateFlow()

    private val _isPlayingLocalFile = MutableStateFlow(false)
    val isPlayingLocalFile: StateFlow<Boolean> = _isPlayingLocalFile.asStateFlow()

    // --- SDK / Core Instances ---
    private var nsdHelper: NsdHelper? = null
    private var signalingServer: SignalingServer? = null
    private var signalingClient: SignalingClient? = null
    private var webRtcManager: WebRtcManager? = null
    private var localAudioPlayer: LocalAudioPlayer? = null

    init {
        nsdHelper = NsdHelper(context)
        localAudioPlayer = LocalAudioPlayer(context)

        // Sync Audio Player state into ViewModel state
        viewModelScope.launch {
            localAudioPlayer?.audioAmplitude?.collect { amplitude ->
                if (_currentRole.value == "Broadcaster") {
                    _audioAmplitude.value = amplitude
                }
            }
        }
        viewModelScope.launch {
            localAudioPlayer?.currentTrackName?.collect { trackName ->
                _playbackTrackName.value = trackName
            }
        }
        viewModelScope.launch {
            localAudioPlayer?.isPlaying?.collect { playing ->
                _isPlayingLocalFile.value = playing
            }
        }

        // Initialize IP
        val localIp = NsdHelper.getLocalIpAddress() ?: "127.0.0.1"
        _broadcasterIp.value = localIp
    }

    fun setRole(role: String) {
        if (_currentRole.value == role) return
        cleanUpAll()
        _currentRole.value = role
        Log.d("LocalIemViewModel", "Role changed to: $role")

        if (role == "Broadcaster") {
            val localIp = NsdHelper.getLocalIpAddress() ?: "127.0.0.1"
            _broadcasterIp.value = localIp
            localAudioPlayer?.loadDefaultHighResTone()
        } else if (role == "Receiver") {
            // Start listening for nearby broadcasters
            startServiceDiscovery()
        }
    }

    // --- FILE PICKING ---
    fun selectLocalAudioFile(uri: Uri, fileName: String) {
        localAudioPlayer?.loadAudioFile(uri, fileName)
    }

    fun toggleLocalFilePlayback() {
        val playing = _isPlayingLocalFile.value
        if (playing) {
            localAudioPlayer?.pause()
        } else {
            localAudioPlayer?.play()
        }
    }

    // --- BROADCASTER CONTROL ---
    fun startBroadcaster() {
        if (_isBroadcasting.value) return
        Log.d("LocalIemViewModel", "Starting Broadcaster session...")

        _isBroadcasting.value = true
        val localIp = NsdHelper.getLocalIpAddress() ?: "127.0.0.1"
        _broadcasterIp.value = localIp

        // 1. Initialize WebRTC
        webRtcManager = WebRtcManager(
            context = context,
            onIceCandidateGathered = { candidate ->
                // Send candidate to connected WebSockets client
                signalingServer?.sendMessage(
                    SignalingMessage(
                        type = "candidate",
                        candidate = candidate.sdp,
                        sdpMid = candidate.sdpMid,
                        sdpMLineIndex = candidate.sdpMLineIndex
                    )
                )
            },
            onAudioTrackReceived = {
                // Monitor track or display incoming stream stats
                Log.d("LocalIemViewModel", "Broadcaster loopback received track info")
            }
        )
        webRtcManager?.initPeerConnection()
        webRtcManager?.setMonitorLocal(_monitorLocalEnabled.value)

        // 2. Start Signaling Server (Ktor Websockets)
        signalingServer = SignalingServer(
            port = 8080,
            onMessageReceived = { message ->
                handleBroadcasterSignalingMessage(message)
            },
            onClientConnected = {
                _isReceiverConnected.value = true
                // Create Offer to initiate exchange with connected WebSockets receiver
                viewModelScope.launch(Dispatchers.Main) {
                    webRtcManager?.createOffer { localOffer ->
                        signalingServer?.sendMessage(
                            SignalingMessage(
                                type = "offer",
                                sdp = localOffer.description
                            )
                        )
                    }
                }
            },
            onClientDisconnected = {
                _isReceiverConnected.value = false
            }
        )
        signalingServer?.start()

        // 3. Register Network Service (NSD) for Auto-Discovery
        nsdHelper?.registerService(8080) { registeredAs ->
            Log.d("LocalIemViewModel", "Successfully announced local service as: $registeredAs")
        }
    }

    fun stopBroadcaster() {
        if (!_isBroadcasting.value) return
        Log.d("LocalIemViewModel", "Stopping Broadcaster session...")

        localAudioPlayer?.stop()
        nsdHelper?.stopRegistration()
        signalingServer?.stop()
        webRtcManager?.close()

        signalingServer = null
        webRtcManager = null

        _isBroadcasting.value = false
        _isReceiverConnected.value = false
    }

    fun toggleMonitorLocal() {
        val newVal = !_monitorLocalEnabled.value
        _monitorLocalEnabled.value = newVal
        webRtcManager?.setMonitorLocal(newVal)
    }

    private fun handleBroadcasterSignalingMessage(message: SignalingMessage) {
        viewModelScope.launch(Dispatchers.Main) {
            when (message.type) {
                "answer" -> {
                    message.sdp?.let { sdpText ->
                        webRtcManager?.setRemoteDescription(sdpText, SessionDescription.Type.ANSWER)
                    }
                }
                "candidate" -> {
                    if (message.candidate != null && message.sdpMid != null && message.sdpMLineIndex != null) {
                        val candidate = org.webrtc.IceCandidate(
                            message.sdpMid,
                            message.sdpMLineIndex,
                            message.candidate
                        )
                        webRtcManager?.addRemoteIceCandidate(candidate)
                    }
                }
            }
        }
    }

    // --- RECEIVER CONTROL ---
    fun updateTargetIp(ipAddress: String) {
        _targetBroadcasterIp.value = ipAddress
    }

    fun startServiceDiscovery() {
        nsdHelper?.discoverServices { hostAddress, port ->
            _targetBroadcasterIp.value = hostAddress
            Log.d("LocalIemViewModel", "NSD Auto-detected Broadcaster IP: $hostAddress:$port")
        }
    }

    fun connectToBroadcaster() {
        val targetIp = _targetBroadcasterIp.value
        if (targetIp.isEmpty() || _isReceiving.value) return
        Log.d("LocalIemViewModel", "Connecting to Broadcaster: $targetIp")

        _isConnectingToBroadcaster.value = true

        // 1. Initialize WebRTC
        webRtcManager = WebRtcManager(
            context = context,
            onIceCandidateGathered = { candidate ->
                signalingClient?.sendMessage(
                    SignalingMessage(
                        type = "candidate",
                        candidate = candidate.sdp,
                        sdpMid = candidate.sdpMid,
                        sdpMLineIndex = candidate.sdpMLineIndex
                    )
                )
            },
            onAudioTrackReceived = { track ->
                Log.d("LocalIemViewModel", "Receiver received high-definition AudioTrack!")
                // Unzip track context inside local receiver
                viewModelScope.launch {
                    track.setEnabled(true)
                    track.setVolume(_receiverVolume.value.toDouble())
                    // Dynamic waveform simulation to match incoming audio track power
                    simulateReceiverAudioVisualizer()
                }
            }
        )
        webRtcManager?.initPeerConnection()

        // 2. Connect via Signaling Websocket (OkHttp Client)
        signalingClient = SignalingClient(
            hostAddress = targetIp,
            port = 8080,
            onMessageReceived = { message ->
                handleReceiverSignalingMessage(message)
            },
            onConnected = {
                _isConnectingToBroadcaster.value = false
                _isReceiving.value = true
            },
            onDisconnected = {
                viewModelScope.launch(Dispatchers.Main) {
                    stopReceiver()
                }
            }
        )
        signalingClient?.connect()
    }

    fun stopReceiver() {
        _isReceiving.value = false
        _isConnectingToBroadcaster.value = false
        _audioAmplitude.value = 0f

        signalingClient?.disconnect()
        webRtcManager?.close()

        signalingClient = null
        webRtcManager = null
    }

    fun updateReceiverVolume(volume: Float) {
        _receiverVolume.value = volume
        webRtcManager?.setVolume(volume)
    }

    private fun handleReceiverSignalingMessage(message: SignalingMessage) {
        viewModelScope.launch(Dispatchers.Main) {
            when (message.type) {
                "offer" -> {
                    message.sdp?.let { sdpText ->
                        webRtcManager?.setRemoteDescription(sdpText, SessionDescription.Type.OFFER) {
                            webRtcManager?.createAnswer { localAnswer ->
                                signalingClient?.sendMessage(
                                    SignalingMessage(
                                        type = "answer",
                                        sdp = localAnswer.description
                                    )
                                )
                            }
                        }
                    }
                }
                "candidate" -> {
                    if (message.candidate != null && message.sdpMid != null && message.sdpMLineIndex != null) {
                        val candidate = org.webrtc.IceCandidate(
                            message.sdpMid,
                            message.sdpMLineIndex,
                            message.candidate
                        )
                        webRtcManager?.addRemoteIceCandidate(candidate)
                    }
                }
            }
        }
    }

    private fun simulateReceiverAudioVisualizer() {
        viewModelScope.launch(Dispatchers.Default) {
            var waveX = 0f
            while (_isReceiving.value) {
                // Generate incoming signal waveform visualization
                val base = kotlin.math.sin(waveX) * 0.35f + kotlin.math.sin(waveX * 2.8f) * 0.2f
                _audioAmplitude.value = (kotlin.math.abs(base) + 0.05f).coerceIn(0f, 1f)
                waveX += 0.2f
                kotlinx.coroutines.delay(40)
            }
            _audioAmplitude.value = 0f
        }
    }

    // --- CLEAN UP ---
    private fun cleanUpAll() {
        try {
            localAudioPlayer?.stop()
        } catch (ignored: Exception) {}

        try {
            nsdHelper?.stopRegistration()
            nsdHelper?.stopDiscovery()
        } catch (ignored: Exception) {}

        try {
            signalingServer?.stop()
        } catch (ignored: Exception) {}

        try {
            signalingClient?.disconnect()
        } catch (ignored: Exception) {}

        try {
            webRtcManager?.close()
        } catch (ignored: Exception) {}

        signalingServer = null
        signalingClient = null
        webRtcManager = null

        _isBroadcasting.value = false
        _isReceiverConnected.value = false
        _isReceiving.value = false
        _isConnectingToBroadcaster.value = false
        _audioAmplitude.value = 0f
    }

    override fun onCleared() {
        super.onCleared()
        cleanUpAll()
    }
}
