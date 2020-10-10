package com.highserpot.bubbling.effect

import android.util.Log
import com.highserpot.bubbling.AudioActivity

interface EffectService{
     val LOG_TAG: String
         get() = "EffectService"

    fun play(){
        Log.d("EffectService","play")
    }
}