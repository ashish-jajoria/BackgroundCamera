package `in`.backgroundcamera

import android.content.Context
import android.media.MediaPlayer


class SoundUtils {

    companion object {
        fun playSound(sound: String?, context: Context) {
            try {
                val mediaPlayer: MediaPlayer =
                    MediaPlayer.create(context,
                        context.resources.getIdentifier(sound, "raw", context.packageName))
                mediaPlayer.start()
                mediaPlayer.setOnCompletionListener { mp -> mp.release() }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}