package co.nayan.login.config

import android.content.Context
import co.nayan.login.R
import co.nayan.login.utils.NetworkUtils
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException
import java.lang.reflect.UndeclaredThrowableException
import kotlin.jvm.Throws

class ConnectivityInterceptor(private val context: Context) : Interceptor {

    @Throws(IOException::class)
    override fun intercept(chain: Interceptor.Chain): Response {
        try {
            if (!NetworkUtils.isOnline(context)) {
                throw IOException(context.getString(R.string.check_your_internet_connection))
            }
        } catch (e: UndeclaredThrowableException) {
            e.printStackTrace()
        }
        return chain.proceed(chain.request().newBuilder().build())
    }
}