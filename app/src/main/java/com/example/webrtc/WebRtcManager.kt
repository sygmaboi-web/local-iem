package com.example.webrtc

import android.content.Context
import android.util.Log
import org.webrtc.*
import org.webrtc.audio.JavaAudioDeviceModule
import java.util.ArrayList

class WebRtcManager(
    private val context: Context,
    private val onIceCandidateGathered: (IceCandidate) -> Unit,
    private val onAudioTrackReceived: (AudioTrack) -> Unit
) {
    private var peerConnectionFactory: PeerConnectionFactory? = null
    private var peerConnection: PeerConnection? = null
    private var localAudioSource: AudioSource? = null
    private var localAudioTrack: AudioTrack? = null

    init {
        initPeerConnectionFactory()
    }

    private fun initPeerConnectionFactory() {
        val options = PeerConnectionFactory.InitializationOptions.builder(context)
            .setEnableInternalTracer(false)
            .createInitializationOptions()
        PeerConnectionFactory.initialize(options)

        // Custom Audio Device Module optimized for High-Fidelity Audio
        // Disabling AEC, NS, and HPF to preserve full audio fidelity for IEM monitoring!
        val audioDeviceModule = JavaAudioDeviceModule.builder(context)
            .setUseHardwareAcousticEchoCanceler(false)
            .setUseHardwareNoiseSuppressor(false)
            .createAudioDeviceModule()

        val factoryOptions = PeerConnectionFactory.Options()
        peerConnectionFactory = PeerConnectionFactory.builder()
            .setOptions(factoryOptions)
            .setAudioDeviceModule(audioDeviceModule)
            .createPeerConnectionFactory()

        audioDeviceModule.release()
    }

    fun mungeSdp(sdpDescription: String): String {
        val lines = sdpDescription.split("\r\n|\n".toRegex())
        val result = StringBuilder()
        var opusPayloadType = ""

        // Find the payload ID inside the dynamic Opus rtpmap
        for (line in lines) {
            if (line.contains("opus/48000")) {
                val match = Regex("rtpmap:(\\d+)").find(line)
                if (match != null) {
                    opusPayloadType = match.groupValues[1]
                }
            }
        }

        // Apply SDP munging for highest audio fidelity settings
        for (line in lines) {
            if (opusPayloadType.isNotEmpty() && line.startsWith("a=fmtp:$opusPayloadType")) {
                Log.d("WebRtcManager", "Munging Opus SDP configuration for high resolution stereo!")
                // Force stereo, disable FEC/DTC (since local LAN is ultra-reliable), max bitrate to max average (510000 bps)
                result.append("a=fmtp:$opusPayloadType minptime=10;useinbandfec=0;stereo=1;sprop-stereo=1;maxaveragebitrate=510000;cbr=1")
                result.append("\r\n")
            } else {
                result.append(line).append("\r\n")
            }
        }
        return result.toString()
    }

    fun initPeerConnection() {
        if (peerConnection != null) return

        val iceServers = arrayListOf(
            PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer()
        )

        val rtcConfig = PeerConnection.RTCConfiguration(iceServers).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
        }

        val observer = object : PeerConnection.Observer {
            override fun onSignalingChange(state: PeerConnection.SignalingState) {}
            override fun onIceConnectionChange(state: PeerConnection.IceConnectionState) {
                Log.d("WebRtcManager", "IceConnectionState changed to: $state")
            }
            override fun onIceConnectionReceivingChange(receiving: Boolean) {}
            override fun onIceGatheringChange(state: PeerConnection.IceGatheringState) {
                Log.d("WebRtcManager", "IceGatheringState changed to: $state")
            }
            override fun onIceCandidate(candidate: IceCandidate) {
                Log.d("WebRtcManager", "Gathered IceCandidate: $candidate")
                onIceCandidateGathered(candidate)
            }
            override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>) {}
            override fun onAddStream(stream: MediaStream) {}
            override fun onRemoveStream(stream: MediaStream) {}
            override fun onDataChannel(channel: DataChannel) {}
            override fun onRenegotiationNeeded() {}
            override fun onAddTrack(receiver: RtpReceiver, mediaStreams: Array<out MediaStream>) {
                Log.d("WebRtcManager", "OnAddTrack received kind: ${receiver.track()?.kind()}")
                val track = receiver.track()
                if (track is AudioTrack) {
                    Log.d("WebRtcManager", "Invoking onAudioTrackReceived...")
                    onAudioTrackReceived(track)
                }
            }
        }

        peerConnection = peerConnectionFactory?.createPeerConnection(rtcConfig, observer)

        // Setup Local Audio Track for Broadcaster
        setupLocalAudioTrack()
    }

    private fun setupLocalAudioTrack() {
        val factory = peerConnectionFactory ?: return
        val pConn = peerConnection ?: return

        // Audio constraints: avoid echoCancellation/noiseSuppression to ensure full high-fidelity spectrum!
        val audioConstraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("echoCancellation", "false"))
            mandatory.add(MediaConstraints.KeyValuePair("noiseSuppression", "false"))
            mandatory.add(MediaConstraints.KeyValuePair("highpassFilter", "false"))
        }

        localAudioSource = factory.createAudioSource(audioConstraints)
        localAudioTrack = factory.createAudioTrack("LocalAudioTrack", localAudioSource)
        localAudioTrack?.setEnabled(true)

        // Add to peer connection as send-only transceiver or standard track
        pConn.addTrack(localAudioTrack, ArrayList(listOf("LocalAudioStream")))
        Log.d("WebRtcManager", "Added local audio track to Peer Connection")
    }

    fun createOffer(onOfferCreated: (SessionDescription) -> Unit) {
        val pConn = peerConnection ?: return
        val sdpConstraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "false"))
        }

        pConn.createOffer(object : SdpObserver {
            override fun onCreateSuccess(desc: SessionDescription) {
                Log.d("WebRtcManager", "Offer created successfully")
                val mungedDescription = SessionDescription(desc.type, mungeSdp(desc.description))
                pConn.setLocalDescription(object : SdpObserver {
                    override fun onCreateSuccess(sdp: SessionDescription?) {}
                    override fun onSetSuccess() {
                        Log.d("WebRtcManager", "Local Offer set successfully")
                        onOfferCreated(mungedDescription)
                    }
                    override fun onCreateFailure(reason: String?) {}
                    override fun onSetFailure(reason: String?) {
                        Log.e("WebRtcManager", "SetLocalDescription failure: $reason")
                    }
                }, mungedDescription)
            }

            override fun onSetSuccess() {}
            override fun onCreateFailure(reason: String?) {
                Log.e("WebRtcManager", "CreateOffer failure: $reason")
            }
            override fun onSetFailure(reason: String?) {}
        }, sdpConstraints)
    }

    fun createAnswer(onAnswerCreated: (SessionDescription) -> Unit) {
        val pConn = peerConnection ?: return
        val sdpConstraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "false"))
        }

        pConn.createAnswer(object : SdpObserver {
            override fun onCreateSuccess(desc: SessionDescription) {
                Log.d("WebRtcManager", "Answer created successfully")
                val mungedDescription = SessionDescription(desc.type, mungeSdp(desc.description))
                pConn.setLocalDescription(object : SdpObserver {
                    override fun onCreateSuccess(sdp: SessionDescription?) {}
                    override fun onSetSuccess() {
                        Log.d("WebRtcManager", "Local Answer set successfully")
                        onAnswerCreated(mungedDescription)
                    }
                    override fun onCreateFailure(reason: String?) {}
                    override fun onSetFailure(reason: String?) {
                        Log.e("WebRtcManager", "SetLocalDescription Answer failure: $reason")
                    }
                }, mungedDescription)
            }

            override fun onSetSuccess() {}
            override fun onCreateFailure(reason: String?) {
                Log.e("WebRtcManager", "CreateAnswer failure: $reason")
            }
            override fun onSetFailure(reason: String?) {}
        }, sdpConstraints)
    }

    fun setRemoteDescription(sdp: String, type: SessionDescription.Type, onSet: () -> Unit = {}) {
        val pConn = peerConnection ?: return
        val sessionDescription = SessionDescription(type, mungeSdp(sdp))
        pConn.setRemoteDescription(object : SdpObserver {
            override fun onCreateSuccess(sdp: SessionDescription?) {}
            override fun onSetSuccess() {
                Log.d("WebRtcManager", "Remote description set successfully")
                onSet()
            }
            override fun onCreateFailure(reason: String?) {}
            override fun onSetFailure(reason: String?) {
                Log.e("WebRtcManager", "SetRemoteDescription failure: $reason")
            }
        }, sessionDescription)
    }

    fun addRemoteIceCandidate(candidate: IceCandidate) {
        peerConnection?.addIceCandidate(candidate) ?: Log.e("WebRtcManager", "Add candidate error: PeerConnection is null")
    }

    fun setVolume(volumeFactor: Float) {
        // volumeFactor should be 0.0f to 10.0f (WebRTC allows overdrive volume up to 10.0f)
        localAudioTrack?.setVolume(volumeFactor.toDouble())
    }

    fun setMonitorLocal(enabled: Boolean) {
        // For Broadcaster: enables or disables playing back local microphone directly to earpieces
        Log.d("WebRtcManager", "Toggle local audio track monitoring: $enabled")
        localAudioTrack?.setEnabled(enabled)
    }

    fun close() {
        try {
            localAudioTrack?.dispose()
            localAudioSource?.dispose()
            peerConnection?.dispose()
            peerConnectionFactory?.dispose()
        } catch (ignored: Exception) {}
        localAudioTrack = null
        localAudioSource = null
        peerConnection = null
        peerConnectionFactory = null
    }
}
