package io.github.abdulroufsidhu.easy_twilio_caller

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

class EasyTwilioCallRecorder {

    private var audioRecord: AudioRecord? = null
    private var recordingThread: Thread? = null
    private var isRecording = false

    fun startRecording(filePath: String) {
        val bufferSize = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )

        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.VOICE_COMMUNICATION,
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSize
        )

        val audioData = ByteArray(bufferSize)
        isRecording = true

        recordingThread = Thread {
            val outputFile = File(filePath)
            val outputStream = FileOutputStream(outputFile)

            try {
                audioRecord?.startRecording()

                while (isRecording) {
                    val bytesRead = audioRecord?.read(audioData, 0, bufferSize) ?: 0
                    if (bytesRead > 0) {
                        outputStream.write(audioData, 0, bytesRead)
                    }
                }

                audioRecord?.stop()
                outputStream.close()
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }

        recordingThread?.start()
    }

    fun stopRecording() {
        isRecording = false
        recordingThread?.join()
        audioRecord?.release()
    }

    companion object {
        private const val SAMPLE_RATE = 16000
    }
}