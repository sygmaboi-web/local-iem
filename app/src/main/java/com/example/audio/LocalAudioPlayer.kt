package com.example.audio

import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.abs
import kotlin.math.sin

class LocalAudioPlayer(private val context: Context) {
    private var mediaPlayer: MediaPlayer? = null
    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying

    private val _currentTrackName = MutableStateFlow("Aucun fichier")
    val currentTrackName: StateFlow<String> = _currentTrackName

    private val _audioAmplitude = MutableStateFlow(0f)
    val audioAmplitude: StateFlow<Float> = _audioAmplitude

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var amplitudeJob: Job? = null

    fun loadAudioFile(uri: Uri, fileName: String) {
        stop()
        try {
            mediaPlayer = MediaPlayer().apply {
                setDataSource(context, uri)
                prepare()
                isLooping = true
            }
            _currentTrackName.value = fileName
            Log.d("LocalAudioPlayer", "Successfully loaded custom file: $fileName")
        } catch (e: Exception) {
            Log.e("LocalAudioPlayer", "Error loading audio file", e)
            _currentTrackName.value = "Error loading $fileName"
        }
    }

    // Play default test tone or local audio
    fun loadDefaultHighResTone() {
        stop()
        _currentTrackName.value = "Studio Test Signal 24-bit 96kHz (Simulator)"
    }

    fun play() {
        if (mediaPlayer != null) {
            mediaPlayer?.start()
            _isPlaying.value = true
            startAmplitudeTracking()
        } else {
            // Simulated player for high res audio
            _isPlaying.value = true
            startAmplitudeTracking()
        }
    }

    fun pause() {
        mediaPlayer?.pause()
        _isPlaying.value = false
        stopAmplitudeTracking()
    }

    fun stop() {
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
        } catch (ignored: Exception) {}
        mediaPlayer = null
        _isPlaying.value = false
        stopAmplitudeTracking()
    }

    private fun startAmplitudeTracking() {
        amplitudeJob?.cancel()
        amplitudeJob = scope.launch(Dispatchers.Default) {
            var tick = 0f
            while (isActive) {
                if (_isPlaying.value) {
                    // Combine a standard complex sine wave to simulate real master audio spectrum
                    val baseWave = abs(sin(tick) * 0.4f + sin(tick * 3.5f) * 0.25f + sin(tick * 8.2f) * 0.15f)
                    val randomNoise = (0..10).random() / 100f
                    val finalAmplitude = (baseWave + randomNoise).coerceIn(0.01f, 1f)
                    _audioAmplitude.value = finalAmplitude
                    tick += 0.15f
                } else {
                    _audioAmplitude.value = 0f
                }
                delay(40) // 25 fps spectrum
            }
        }
    }

    private fun stopAmplitudeTracking() {
        amplitudeJob?.cancel()
        _audioAmplitude.value = 0f
    }
}
