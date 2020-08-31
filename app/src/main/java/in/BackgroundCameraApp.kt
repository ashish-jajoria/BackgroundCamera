package `in`

import android.app.Application
import androidx.camera.camera2.Camera2Config
import androidx.camera.core.CameraX
import androidx.camera.core.CameraXConfig

class BackgroundCameraApp: Application(), CameraXConfig.Provider {
    override fun getCameraXConfig(): CameraXConfig {
        return Camera2Config.defaultConfig()
    }
}