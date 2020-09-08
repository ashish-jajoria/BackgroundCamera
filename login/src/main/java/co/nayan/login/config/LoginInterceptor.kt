package co.nayan.login.config

import okhttp3.Interceptor
import okhttp3.Response

class LoginInterceptor(private val loginConfig: LoginConfig) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val response = chain.proceed(chain.request().newBuilder().build())
        loginConfig.getAuthManager().saveAuthenticationHeaders(response.headers)
        return response
    }
}
