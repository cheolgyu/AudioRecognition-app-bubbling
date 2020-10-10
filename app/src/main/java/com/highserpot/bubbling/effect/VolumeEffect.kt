package com.highserpot.bubbling.effect

import android.R
import android.content.ContentResolver
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.RingtoneManager
import android.media.SoundPool
import android.net.Uri
import android.os.Build
import android.util.Log
import com.highserpot.bubbling.use_volume


class VolumeEffect(val ctx: Context) : EffectService {
    private var soundPool: SoundPool? = null
    private val soundIds: Array<Int> = arrayOf(0, 0, 0, 0, 0, 0, 0, 0)
    var sound = 1

    init {
//        soundPool = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
//            val audioAttributes = AudioAttributes.Builder()
//                .setUsage(AudioAttributes.USAGE_NOTIFICATION)
//                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
//                .build()
//            SoundPool.Builder().setAudioAttributes(audioAttributes).setMaxStreams(8).build()
//        } else {
//            SoundPool(8, AudioManager.STREAM_NOTIFICATION, 0)
//        }
//

        val ringtoneUri: Uri? =
            RingtoneManager.getActualDefaultRingtoneUri(ctx, RingtoneManager.TYPE_NOTIFICATION)
        soundPool = SoundPool(1, AudioManager.STREAM_MUSIC, 0)

        val resolver: ContentResolver = ctx.getContentResolver()
        try {
            resolver.openAssetFileDescriptor(ringtoneUri!!, "r")
                .use { afd -> sound = soundPool!!.load(afd, 1) }
        } catch (e: Exception) {
            Log.e(
                "SOUNDPOOL",
                "Couldn't open " + if (ringtoneUri == null) "null uri" else ringtoneUri.toString()

            )
        }

//        soundPool!!.setOnLoadCompleteListener { soundPool, sampleId, status ->
//            soundPool.play(
//                sampleId,
//                1f,
//                1f,
//                0,
//                0,
//                1f
//            )
//        }
    }

    override fun play() {
        super.play()
        if (use_volume) {
            soundPool!!.play(sound, 1f, 1f, 0, 0, 1f);
        }
    }


}