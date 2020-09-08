package co.nayan.login.config

import androidx.appcompat.app.AppCompatActivity

interface LoginConfig {

    /**
     * Provide the base url for login API
     */
    fun apiBaseUrl(): String

    /**
     * Provide the activity class to be opened on successful login
     */
    fun mainActivityClass(): Class<out AppCompatActivity>

    /***
     * provide authentication headers to app module
     */
    fun getAuthManager() : AuthManager
}