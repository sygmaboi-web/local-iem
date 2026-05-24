package com.example.network

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

data class SignalingMessage(
    val type: String,               // "offer", "answer", "candidate"
    val sdp: String? = null,
    val candidate: String? = null,
    val sdpMid: String? = null,
    val sdpMLineIndex: Int? = null
) {
    companion object {
        private val moshi = Moshi.Builder()
            .addLast(KotlinJsonAdapterFactory())
            .build()
        private val adapter = moshi.adapter(SignalingMessage::class.java)

        fun fromJson(json: String): SignalingMessage? {
            return try {
                adapter.fromJson(json)
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }

        fun toJson(message: SignalingMessage): String {
            return adapter.toJson(message)
        }
    }
}
