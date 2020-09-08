package co.nayan.login.config

import okhttp3.Headers

interface AuthManager {
    fun saveAuthenticationHeaders(headers: Headers)
}