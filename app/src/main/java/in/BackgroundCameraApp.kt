package `in`

import `in`.backgroundcamera.BuildConfig
import `in`.di.backgroundCameraModule
import android.app.Application
import android.content.Context
import androidx.camera.camera2.Camera2Config
import androidx.camera.core.CameraXConfig
import co.nayan.login.di.loginModule
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import timber.log.Timber

class BackgroundCameraApp : Application(), CameraXConfig.Provider {

    override fun onCreate() {
        super.onCreate()

        startKoin {
            androidLogger()
            androidContext(this@BackgroundCameraApp)
            modules(listOf(backgroundCameraModule, loginModule))
        }
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
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