package `in`.backgroundcamera

import android.app.Service
import android.content.Intent
import android.os.IBinder

class FloatingViewService : Service() {
    override fun onBind(intent: Intent): IBinder? {
        return null
    }
}
