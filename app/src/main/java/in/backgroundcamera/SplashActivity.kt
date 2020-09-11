package `in`.backgroundcamera

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import androidx.appcompat.app.AppCompatActivity

class SplashActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        Handler().postDelayed(
            {
                Intent(this@SplashActivity, MainActivity::class.java).apply {
                    finish()
                    startActivity(this)
                }
            }, 1_000L
        )
    }
}