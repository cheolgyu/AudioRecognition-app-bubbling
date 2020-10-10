package com.highserpot.bubbling.effect

import android.animation.AnimatorInflater
import android.animation.AnimatorSet
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.ListView
import com.highserpot.bubbling.R

class LabelColorEffect(var ctx: Context, var labelsListView: ListView) {
    fun play(labelIndex: Int) {
        Handler(Looper.getMainLooper()).post(Runnable {
            val labelView = labelsListView.getChildAt(labelIndex - 2)
            val colorAnimation = AnimatorInflater.loadAnimator(
                ctx, R.animator.color_animation
            ) as AnimatorSet
            colorAnimation.setTarget(labelView)
            colorAnimation.start()
        })
    }

}