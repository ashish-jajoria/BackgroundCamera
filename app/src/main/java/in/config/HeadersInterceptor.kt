package `in`.config

import android.content.Context
import android.content.Intent
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import okhttp3.Headers
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response

class HeadersInterceptor(private val sharedStorage: SharedStorage, private val context: Context) :
    Interceptor {

    private var accessToken: String? = null
    private var client: String? = null
    private var expiry: String? = null
    private var uid: String? = null

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest: Request = chain.request()

        val headers = Headers.Builder()
            .add("api-key", Constants.READ_LP_API_KEY)
            .add("content-type", "application/json")
            .add("token-type", "Bearer")
            .add("access-token", sharedStorage.getAccessToken())
            .add("client", sharedStorage.getClient())
            .add("expiry", sharedStorage.getExpiry())
            .add("uid", sharedStorage.getUID())
            .build()

        val newRequest = originalRequest.newBuilder()
            .headers(headers)
            .build()

        val response = chain.proceed(newRequest)

        when (response.code) {
            401 -> {
                removeHeaders()
                LocalBroadcastManager.getInstance(context)
                    .sendBroadcast(Intent(Constants.UNAUTHORIZED))
            }
            500 -> {
                LocalBroadcastManager.getInstance(context)
                    .sendBroadcast(Intent(Constants.INTERNAL_SERVER_ERROR))
            }
            else -> {
                accessToken = response.headers["access-token"].toString()
                accessToken?.let { sharedStorage.setAccessToken(it) }

                client = response.headers["client"].toString()
                client?.let { sharedStorage.setClient(it) }

                expiry = response.headers["expiry"].toString()
                expiry?.let { sharedStorage.setExpiry(it) }

                uid = response.headers["uid"].toString()
                uid?.let { sharedStorage.setUID(it) }
            }
        }
        return response
    }

    private fun removeHeaders() {
        accessToken = null
        client = null
        expiry = null
        uid = null
    }
}
