package co.nayan.login.utils

import android.content.Context
import android.net.ConnectivityManager

class NetworkUtils {

    companion object {
        fun isOnline(context: Context): Boolean {
            val connectivityManager =
                context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val networkInfo = connectivityManager.activeNetworkInfo
            if (networkInfo != null) {
                if (networkInfo.isConnected) {
                    return true
                }
            }
            return false
        }
    }

}