package `in`.config

import `in`.backgroundcamera.MainActivity
import androidx.appcompat.app.AppCompatActivity
import co.nayan.login.config.AuthManager
import co.nayan.login.config.LoginConfig

class LoginConfigImpl(private val authManager: AuthManager) : LoginConfig {
    override fun getAuthManager(): AuthManager {
        return authManager
    }

    override fun apiBaseUrl(): String {
        return Constants.BASE_URL
    }

    override fun mainActivityClass(): Class<out AppCompatActivity> {
        return MainActivity::class.java
    }
}