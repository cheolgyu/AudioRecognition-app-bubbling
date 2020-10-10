package com.highserpot.bubbling.effect

import android.content.Context
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import androidx.core.content.ContextCompat.getSystemService
import com.highserpot.bubbling.MINIMUM_TIME_BETWEEN_SAMPLES_MS


class VibratorEffect(var ctx: Context)  : EffectService {
    override fun play() {
        super.play()
        val vibrator = ctx.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator?

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            vibrator!!.vibrate(VibrationEffect.createOneShot(MINIMUM_TIME_BETWEEN_SAMPLES_MS /2,255))
        }else{
            vibrator!!.vibrate(MINIMUM_TIME_BETWEEN_SAMPLES_MS /2)
        }

    }
}