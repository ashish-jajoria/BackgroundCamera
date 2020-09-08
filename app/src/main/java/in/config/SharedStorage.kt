package `in`.config

import android.content.Context

class SharedStorage(context: Context) {

    private val sharedPreferences =
        context.getSharedPreferences(Storage.C3_SPECIALIST_STORAGE, Context.MODE_PRIVATE)

    fun setUserProfileInfo(userInfo: String) {
        sharedPreferences.edit().putString(Storage.USER_INFO, userInfo).apply()
    }

    fun isUserLoggedIn(): Boolean {
        return sharedPreferences.getBoolean(Storage.IS_LOGGED_IN, false)
    }

    fun setUserLoggedInStatus(status: Boolean) {
        sharedPreferences.edit().putBoolean(Storage.IS_LOGGED_IN, status).apply()
    }

    fun setAccessToken(acessToken: String) {
        sharedPreferences.edit().putString(Storage.ACCESS_TOKEN, acessToken).apply()
    }

    fun getAccessToken(): String {
        return sharedPreferences.getString(Storage.ACCESS_TOKEN, "").toString()
    }

    fun setClient(client: String) {
        sharedPreferences.edit().putString(Storage.CLIENT, client).apply()
    }

    fun getClient(): String {
        return sharedPreferences.getString(Storage.CLIENT, "").toString()
    }

    fun setExpiry(expiry: String) {
        sharedPreferences.edit().putString(Storage.EXPIRY, expiry).apply()
    }

    fun getExpiry(): String {
        return sharedPreferences.getString(Storage.EXPIRY, "").toString()
    }

    fun setUID(uid: String) {
        sharedPreferences.edit().putString(Storage.UID, uid).apply()
    }

    fun getUID(): String {
        return sharedPreferences.getString(Storage.UID, "").toString()
    }
}