package `in`.config

import co.nayan.login.config.AuthManager
import okhttp3.Headers

class AuthManagerImpl(private val sharedStorage: SharedStorage) : AuthManager {

    override fun saveAuthenticationHeaders(headers: Headers) {
        sharedStorage.setAccessToken(headers["access-token"].toString())
        sharedStorage.setClient(headers["client"].toString())
        sharedStorage.setExpiry(headers["expiry"].toString())
        sharedStorage.setUID(headers["uid"].toString())
    }

}