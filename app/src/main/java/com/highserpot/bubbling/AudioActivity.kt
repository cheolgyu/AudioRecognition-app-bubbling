package com.highserpot.bubbling

import android.Manifest
import android.animation.AnimatorInflater
import android.animation.AnimatorSet
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.util.Log
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ListView
import androidx.appcompat.app.AppCompatActivity
import com.highserpot.bubbling.`interface`.LabelColor
import com.highserpot.bubbling.utils.Recognition
import java.util.*
import java.util.concurrent.locks.ReentrantLock


val SAMPLE_RATE = 16000
val SAMPLE_DURATION_MS = 1000
val RECORDING_LENGTH = (SAMPLE_RATE * SAMPLE_DURATION_MS / 1000)
var labels: ArrayList<String> = ArrayList()
var displayedLabels: ArrayList<String> = ArrayList()
val recordingBufferLock = ReentrantLock()
val sampleRateList = intArrayOf(SAMPLE_RATE)
var recordingBuffer = ShortArray(RECORDING_LENGTH)
var recordingOffset = 0
var shouldContinueRecognition = true
var shouldContinue = true

open class AudioActivity : AppCompatActivity() {
    // Constants that control the behavior of the recognition code and model
    // settings. See the audio recognition tutorial for a detailed explanation of
    // all these, but you should customize them to match your training settings if
    // you are running your own model.

    // UI elements.
    private val REQUEST_RECORD_AUDIO = 4
    private var quitButton: Button? = null
    private var labelsListView: ListView? = null
    private val LOG_TAG = AudioActivity::class.java.simpleName

    // Working variables.
    var recognition: Recognition = Recognition()
    private var recordingThread: Thread? = null
    lateinit var labelColor: LabelColor


    override fun onCreate(savedInstanceState: Bundle?) {
        // Set up the UI.
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_audio)


        quitButton = findViewById<View>(R.id.quit) as Button
        quitButton!!.setOnClickListener {
            moveTaskToBack(true)
            Process.killProcess(Process.myPid())
            System.exit(1)
        }
        labelsListView = findViewById<View>(R.id.list_view) as ListView


        // Build a list view based on these labels.
        val arrayAdapter = ArrayAdapter(this, R.layout.list_text_item, displayedLabels)
        labelsListView!!.adapter = arrayAdapter
        labelColor = LabelColor(this, labelsListView!!)
        // Set up an object to smooth recognition results to increase accuracy.
        recognition.init(assets, labelColor)


        // Start the recording and recognition threads.
        requestMicrophonePermission()
        startRecording()
        recognition.startRecognition()
    }

    private fun requestMicrophonePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST_RECORD_AUDIO)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {

        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_RECORD_AUDIO && grantResults.size > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startRecording()
            recognition.startRecognition()
        }
    }


    @Synchronized
    fun startRecording() {
        if (recordingThread != null) {
            return
        }
        shouldContinue = true
        recordingThread = Thread { record() }
        recordingThread!!.start()
    }

    @Synchronized
    fun stopRecording() {
        if (recordingThread == null) {
            return
        }
        shouldContinue = false
        recordingThread = null
    }

    private fun record() {
        Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO)

        // Estimate the buffer size we'll need for this device.
        var bufferSize = AudioRecord.getMinBufferSize(
            SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        if (bufferSize == AudioRecord.ERROR || bufferSize == AudioRecord.ERROR_BAD_VALUE) {
            bufferSize = SAMPLE_RATE * 2
        }
        val audioBuffer = ShortArray(bufferSize / 2)
        val record = AudioRecord(
            MediaRecorder.AudioSource.DEFAULT,
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSize
        )
        if (record.state != AudioRecord.STATE_INITIALIZED) {
            Log.e(LOG_TAG, "Audio Record can't initialize!")
            return
        }
        record.startRecording()
        Log.v(LOG_TAG, "Start recording")

        // Loop, gathering audio data and copying it to a round-robin buffer.
        while (shouldContinue) {
            val numberRead = record.read(audioBuffer, 0, audioBuffer.size)
            val maxLength = recordingBuffer.size
            val newRecordingOffset = recordingOffset + numberRead
            val secondCopyLength = Math.max(0, newRecordingOffset - maxLength)
            val firstCopyLength = numberRead - secondCopyLength
            // We store off all the data for the recognition thread to access. The ML
            // thread will copy out of this buffer into its own, while holding the
            // lock, so this should be thread safe.
            recordingBufferLock.lock()
            recordingOffset = try {
                System.arraycopy(audioBuffer, 0, recordingBuffer, recordingOffset, firstCopyLength)
                System.arraycopy(audioBuffer, firstCopyLength, recordingBuffer, 0, secondCopyLength)
                newRecordingOffset % maxLength
            } finally {
                recordingBufferLock.unlock()
            }
        }
        record.stop()
        record.release()
    }


}