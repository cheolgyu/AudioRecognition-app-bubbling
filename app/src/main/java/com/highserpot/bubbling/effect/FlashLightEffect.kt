package com.highserpot.bubbling.effect

import android.content.Context
import android.content.pm.PackageManager
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import com.highserpot.bubbling.MINIMUM_TIME_BETWEEN_SAMPLES_MS
import com.highserpot.bubbling.use_flashlight


class FlashLightEffect(val ctx: Context) : EffectService {
    override fun play() {
        super.play()
        turnOnFlashLight()
    }
    var isFlashOn = false

    var hasFlash = false
    lateinit var mCameraManager: CameraManager
    lateinit var mCameraId: String

    init {
        checkFlash()
        mCameraManager = ctx.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        try {
            mCameraId = mCameraManager.cameraIdList[0]
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        }
    }

    fun turnOnFlashLight() {
        if (use_flashlight && ::mCameraManager.isInitialized ){
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    mCameraManager.setTorchMode(mCameraId, true)
                    Handler(Looper.getMainLooper()).postDelayed(Runnable {
                        mCameraManager.setTorchMode(mCameraId, false)
                    }, MINIMUM_TIME_BETWEEN_SAMPLES_MS / 2)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun turnOffFlashLight() {
        if (use_flashlight&& ::mCameraManager.isInitialized){
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    mCameraManager.setTorchMode(mCameraId, false)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

    }


    fun checkFlash() {
        hasFlash = ctx.getApplicationContext().getPackageManager()
            .hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH)
        if (!hasFlash) {
            val text = "has not Flash ㅜㅜ "
            val duration = Toast.LENGTH_SHORT
            val toast = Toast.makeText(ctx, text, duration)
            toast.show()
        } else {

        }
    }
}