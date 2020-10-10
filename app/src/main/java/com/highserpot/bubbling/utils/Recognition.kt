package com.highserpot.bubbling.utils

import android.content.res.AssetManager
import android.util.Log
import com.highserpot.bubbling.*
import com.highserpot.bubbling.effect.FlashLightEffect
import com.highserpot.bubbling.effect.LabelColorEffect
import com.highserpot.bubbling.effect.VibratorEffect
import com.highserpot.bubbling.effect.VolumeEffect
import org.tensorflow.contrib.android.TensorFlowInferenceInterface
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader


class Recognition {

    private val LOG_TAG = "==>" + Recognition::class.java.simpleName
    private val AVERAGE_WINDOW_DURATION_MS: Long = 500
    private val SUPPRESSION_MS = 1500
    private val MINIMUM_COUNT = 3
    private val LABEL_FILENAME = "file:///android_asset/conv_actions_labels.txt"
    private val MODEL_FILENAME = "file:///android_asset/conv_actions_frozen.pb"
    private val INPUT_DATA_NAME = "decoded_sample_data:0"
    private val SAMPLE_RATE_NAME = "decoded_sample_data:1"
    private val OUTPUT_SCORES_NAME = "labels_softmax"
    private var inferenceInterface: TensorFlowInferenceInterface? = null
    private var recognizeCommands: RecognizeCommands? = null


    private var recognitionThread: Thread? = null
    lateinit var labelColorEffect: LabelColorEffect
    lateinit var flashLightEffect: FlashLightEffect
    lateinit var vibratorEffect: VibratorEffect
    lateinit var volumeEffect: VolumeEffect

    fun init(
        assets: AssetManager,
        labelColorEffect: LabelColorEffect,
        flashLightEffect: FlashLightEffect,
        vibratorEffect: VibratorEffect,
        volumeEffect: VolumeEffect
    ) {
        this.labelColorEffect = labelColorEffect
        this.flashLightEffect = flashLightEffect
        this.vibratorEffect = vibratorEffect
        this.volumeEffect = volumeEffect

        load_label(assets)
        // Load the TensorFlow model.
        inferenceInterface = TensorFlowInferenceInterface(assets, MODEL_FILENAME)
        recognizeCommands = RecognizeCommands(
            labels,
            AVERAGE_WINDOW_DURATION_MS,
            DETECTION_THRESHOLD,
            SUPPRESSION_MS,
            MINIMUM_COUNT,
            MINIMUM_TIME_BETWEEN_SAMPLES_MS
        )
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
            if (!result.foundCommand.startsWith("_") && result.isNewCommand) {
                var labelIndex = -1
                for (i in labels.indices) {
                    if (labels[i] == result.foundCommand) {
                        labelIndex = i
                    }
                }
                labelColorEffect.play(labelIndex)
                flashLightEffect.play()
                vibratorEffect.play()
                volumeEffect.play()
            }
            if (BuildConfig.DEBUG) {
//                labelColorEffect.play(2)
//                flashLightEffect.play()
//                vibratorEffect.play()
//                volumeEffect.play()
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

    @Throws(IOException::class)
    fun load_label(assets: AssetManager) {
        // Load the labels for the model, but only display those that don't start
        // with an underscore.
        val actualFilename =
            LABEL_FILENAME.split("file:///android_asset/".toRegex()).toTypedArray()[1]
        Log.i(LOG_TAG, "Reading labels from: $actualFilename")
        var br: BufferedReader? = null
        try {

            br = BufferedReader(InputStreamReader(assets.open(actualFilename)))
            var line: String = ""
            while (true) {
                var _line = br.readLine()
                if (!_line.isNullOrEmpty()) {
                    labels.add(_line)
                    if (_line[0] != '_') {
                        displayedLabels.add(
                            _line.substring(0, 1).toUpperCase() + _line.substring(1)
                        )
                    }
                } else {
                    break
                }
            }
            br.close()
        } catch (e: IOException) {
            throw RuntimeException("Problem reading label file!", e)
        }
    }
}