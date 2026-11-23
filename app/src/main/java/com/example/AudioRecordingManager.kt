package com.example

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import java.io.File

class AudioRecordingManager(private val context: Context) {
    private var mediaRecorder: MediaRecorder? = null
    private var currentOutputFile: File? = null
    var isRecording: Boolean = false
        private set

    fun startRecording(): Boolean {
        return try {
            val file = File(context.cacheDir, "audio_query_${System.currentTimeMillis()}.m4a")
            currentOutputFile = file

            val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }

            recorder.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(128000)
                setAudioSamplingRate(44100)
                setOutputFile(file.absolutePath)
                prepare()
                start()
            }
            mediaRecorder = recorder
            isRecording = true
            true
        } catch (e: Exception) {
            Log.e("AudioRecorder", "Failed to start recording", e)
            isRecording = false
            false
        }
    }

    fun stopRecording(): ByteArray? {
        if (!isRecording) return null
        return try {
            mediaRecorder?.apply {
                try {
                    stop()
                } catch (e: Exception) {
                    Log.e("AudioRecorder", "Error stopping recorder", e)
                }
                release()
            }
            mediaRecorder = null
            isRecording = false

            val file = currentOutputFile
            if (file != null && file.exists() && file.length() > 0) {
                val bytes = file.readBytes()
                file.delete()
                bytes
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e("AudioRecorder", "Failed reading recorded audio bytes", e)
            isRecording = false
            null
        }
    }

    fun cancelRecording() {
        try {
            mediaRecorder?.apply {
                try { stop() } catch (e: Exception) {}
                release()
            }
        } catch (e: Exception) {}
        mediaRecorder = null
        isRecording = false
        currentOutputFile?.delete()
    }
}
