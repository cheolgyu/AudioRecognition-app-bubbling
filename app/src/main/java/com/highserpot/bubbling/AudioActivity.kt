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
import org.tensorflow.contrib.android.TensorFlowInferenceInterface
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.util.*
import java.util.concurrent.locks.ReentrantLock

open class AudioActivity: AppCompatActivity() {
    // Constants that control the behavior of the recognition code and model
    // settings. See the audio recognition tutorial for a detailed explanation of
    // all these, but you should customize them to match your training settings if
    // you are running your own model.
    private val SAMPLE_RATE = 16000
    private val SAMPLE_DURATION_MS = 1000
    private val RECORDING_LENGTH = (SAMPLE_RATE * SAMPLE_DURATION_MS / 1000)
    private val AVERAGE_WINDOW_DURATION_MS: Long = 500
    private val DETECTION_THRESHOLD = 0.70f
    private val SUPPRESSION_MS = 1500
    private val MINIMUM_COUNT = 3
    private val MINIMUM_TIME_BETWEEN_SAMPLES_MS: Long = 30
    private val LABEL_FILENAME = "file:///android_asset/conv_actions_labels.txt"
    private val MODEL_FILENAME = "file:///android_asset/conv_actions_frozen.pb"
    private val INPUT_DATA_NAME = "decoded_sample_data:0"
    private val SAMPLE_RATE_NAME = "decoded_sample_data:1"
    private val OUTPUT_SCORES_NAME = "labels_softmax"

    // UI elements.
    private val REQUEST_RECORD_AUDIO = 4
    private var quitButton: Button? = null
    private var labelsListView: ListView? = null
    private val LOG_TAG = AudioActivity::class.java.simpleName

    // Working variables.
    var recordingBuffer = ShortArray(RECORDING_LENGTH)
    var recordingOffset = 0
    var shouldContinue = true
    private var recordingThread: Thread? = null
    var shouldContinueRecognition = true
    private var recognitionThread: Thread? = null
    private val recordingBufferLock = ReentrantLock()
    private var inferenceInterface: TensorFlowInferenceInterface? = null
    private var labels: ArrayList<String> = ArrayList()
    private var displayedLabels: ArrayList<String> = ArrayList()
    private var recognizeCommands: RecognizeCommands? = null

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

        load_lb()

        // Build a list view based on these labels.
        val arrayAdapter = ArrayAdapter(this, R.layout.list_text_item, displayedLabels)
        labelsListView!!.adapter = arrayAdapter

        // Set up an object to smooth recognition results to increase accuracy.
        recognizeCommands = RecognizeCommands(
            labels,
            AVERAGE_WINDOW_DURATION_MS,
            DETECTION_THRESHOLD,
            SUPPRESSION_MS,
            MINIMUM_COUNT,
            MINIMUM_TIME_BETWEEN_SAMPLES_MS
        )

        // Load the TensorFlow model.
        inferenceInterface = TensorFlowInferenceInterface(applicationContext.assets, MODEL_FILENAME)

        // Start the recording and recognition threads.
        requestMicrophonePermission()
        startRecording()
        startRecognition()
    }

    @Throws(IOException::class)
    private fun load_lb(){
        // Load the labels for the model, but only display those that don't start
        // with an underscore.
        val actualFilename =
            LABEL_FILENAME.split("file:///android_asset/".toRegex()).toTypedArray()[1]
        Log.i(LOG_TAG, "Reading labels from: $actualFilename")
        var br: BufferedReader? = null
        try {

            br = BufferedReader(InputStreamReader(applicationContext.assets.open(actualFilename)))
            var line: String = ""
            while (true) {
                var _line = br.readLine()
                if (!_line.isNullOrEmpty()){
                    Log.e("aaaaaaaaaaaaa",_line)
                    labels.add(_line)
                    if (_line[0] != '_') {
                        displayedLabels.add(_line.substring(0, 1).toUpperCase() + _line.substring(1))
                    }
                }else{
                    break
                }
            }
            br.close()
        } catch (e: IOException) {
            throw RuntimeException("Problem reading label file!", e)
        }
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
            startRecognition()
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

    @Synchronized
    fun startRecognition() {
        if (recognitionThread != null) {
            return
        }
        shouldContinueRecognition = true
        recognitionThread = Thread { recognize() }
        recognitionThread!!.start()
    }

    @Synchronized
    fun stopRecognition() {
        if (recognitionThread == null) {
            return
        }
        shouldContinueRecognition = false
        recognitionThread = null
    }

    private fun recognize() {
        Log.v(LOG_TAG, "Start recognition")
        val inputBuffer = ShortArray(RECORDING_LENGTH)
        val floatInputBuffer = FloatArray(RECORDING_LENGTH)
        val outputScores = FloatArray(labels.size)
        val outputScoresNames = arrayOf(OUTPUT_SCORES_NAME)
        val sampleRateList = intArrayOf(SAMPLE_RATE)

        // Loop, grabbing recorded data and running the recognition model on it.
        while (shouldContinueRecognition) {
            // The recording thread places data in this round-robin buffer, so lock to
            // make sure there's no writing happening and then copy it to our own
            // local version.
            recordingBufferLock.lock()
            try {
                val maxLength = recordingBuffer.size
                val firstCopyLength = maxLength - recordingOffset
                val secondCopyLength = recordingOffset
                System.arraycopy(recordingBuffer, recordingOffset, inputBuffer, 0, firstCopyLength)
                System.arraycopy(recordingBuffer, 0, inputBuffer, firstCopyLength, secondCopyLength)
            } finally {
                recordingBufferLock.unlock()
            }

            // We need to feed in float values between -1.0f and 1.0f, so divide the
            // signed 16-bit inputs.
            for (i in 0 until RECORDING_LENGTH) {
                floatInputBuffer[i] = inputBuffer[i] / 32767.0f
            }

            // Run the model.
            inferenceInterface!!.feed(SAMPLE_RATE_NAME, sampleRateList)
            inferenceInterface!!.feed(
                INPUT_DATA_NAME,
                floatInputBuffer,
                RECORDING_LENGTH.toLong(),
                1
            )
            inferenceInterface!!.run(outputScoresNames)
            inferenceInterface!!.fetch(OUTPUT_SCORES_NAME, outputScores)

            // Use the smoother to figure out if we've had a real recognition event.
            val currentTime = System.currentTimeMillis()
            val result: RecognizeCommands.RecognitionResult =
                recognizeCommands!!.processLatestResults(outputScores, currentTime)
            runOnUiThread { // If we do have a new command, highlight the right list entry.
                if (!result.foundCommand.startsWith("_") && result.isNewCommand) {
                    var labelIndex = -1
                    for (i in labels.indices) {
                        if (labels[i] == result.foundCommand) {
                            labelIndex = i
                        }
                    }
                    val labelView = labelsListView!!.getChildAt(labelIndex - 2)
                    val colorAnimation = AnimatorInflater.loadAnimator(
                        this@AudioActivity, R.animator.color_animation
                    ) as AnimatorSet
                    colorAnimation.setTarget(labelView)
                    colorAnimation.start()
                }
            }
            try {
                // We don't need to run too frequently, so snooze for a bit.
                Thread.sleep(MINIMUM_TIME_BETWEEN_SAMPLES_MS)
            } catch (e: InterruptedException) {
                // Ignore
            }
        }
        Log.v(LOG_TAG, "End recognition")
    }
}