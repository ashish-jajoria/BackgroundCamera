package `in`

import android.app.Application
import android.content.Context
import androidx.camera.camera2.Camera2Config
import androidx.camera.core.CameraXConfig
import timber.log.Timber
import timber.log.Timber.DebugTree

class BackgroundCameraApp: Application(), CameraXConfig.Provider {

    override fun onCreate() {
        super.onCreate()
        Timber.plant(DebugTree())
        setContext(this)
    }

    override fun getCameraXConfig(): CameraXConfig {
        return Camera2Config.defaultConfig()
    }

    companion object {

        private lateinit var context: Context

        fun setContext(con: Context) {
            context = con
        }

        fun getContext(): Context {
            return context
        }
    }
}