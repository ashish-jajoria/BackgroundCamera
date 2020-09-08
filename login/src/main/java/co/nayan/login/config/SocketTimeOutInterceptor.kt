package co.nayan.login.config

import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException
import kotlin.jvm.Throws

class SocketTimeOutInterceptor : Interceptor {

    @Throws(IOException::class)
    override fun intercept(chain: Interceptor.Chain): Response {
        try {
            return chain.proceed(chain.request().newBuilder().build())
        } catch (e: IOException) {
            throw IOException("Something went wrong, please try again later.")
        }
    }
}