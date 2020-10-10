package com.highserpot.bubbling

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ListView
import android.widget.ToggleButton
import androidx.appcompat.app.AppCompatActivity
import com.highserpot.bubbling.effect.FlashLightEffect
import com.highserpot.bubbling.effect.LabelColorEffect
import com.highserpot.bubbling.effect.VibratorEffect
import com.highserpot.bubbling.utils.Recognition
import com.highserpot.bubbling.utils.Recording
import java.util.*
import java.util.concurrent.locks.ReentrantLock

//val DETECTION_THRESHOLD = 0.93f
val DETECTION_THRESHOLD = 0.96f
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
var use_flashlight = false
val MINIMUM_TIME_BETWEEN_SAMPLES_MS: Long = 30

open class AudioActivity : AppCompatActivity() {
    // Constants that control the behavior of the recognition code and model
    // settings. See the audio recognition tutorial for a detailed explanation of
    // all these, but you should customize them to match your training settings if
    // you are running your own model.

    // UI elements.
    private val REQUEST_RECORD_AUDIO = 4
    private var quitButton: Button? = null
    lateinit var toggleFlashLightButton: Button

    private var labelsListView: ListView? = null
    private val LOG_TAG = AudioActivity::class.java.simpleName

    // Working variables.
    val recognition: Recognition = Recognition()
    val recording: Recording = Recording()

    @SuppressLint("UseCompatLoadingForDrawables")
    override fun onCreate(savedInstanceState: Bundle?) {
        // Set up the UI.
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_audio)

        toggleFlashLightButton = findViewById<View>(R.id.toggleFlashLight) as ToggleButton

        toggleFlashLightButton.setOnClickListener {
            it.isActivated
            if ((toggleFlashLightButton as ToggleButton).isChecked) {
                toggleFlashLightButton.background = getDrawable(R.drawable.ic_action_flashlight_off)
                use_flashlight = true
            } else {
                toggleFlashLightButton.background = getDrawable(R.drawable.ic_action_flashlight)
                use_flashlight = false
            }
        }
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
        val labelColorEffect = LabelColorEffect(this, labelsListView!!)
        val flashLightEffect = FlashLightEffect(this)
        val vibratorEffect = VibratorEffect(this)

        recognition.init(assets, labelColorEffect, flashLightEffect, vibratorEffect)


        // Start the recording and recognition threads.
        requestMicrophonePermission()
        start()

        if (BuildConfig.DEBUG) {
            toggleFlashLightButton.performClick()
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
            start()
        }

    }

    private fun start() {
        recording.startRecording()
        recognition.startRecognition()
    }
}