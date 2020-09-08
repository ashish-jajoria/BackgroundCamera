package co.nayan.login.di

import co.nayan.login.LoginRepository
import co.nayan.login.LoginViewModel
import co.nayan.login.api.LoginService
import co.nayan.login.config.LoginConfig
import co.nayan.login.config.LoginInterceptor
import co.nayan.login.config.ConnectivityInterceptor
import co.nayan.login.config.SocketTimeOutInterceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import org.koin.android.ext.koin.androidContext
import org.koin.android.viewmodel.dsl.viewModel
import org.koin.dsl.module
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

val loginModule = module {

    single<LoginService> {
        val logger = HttpLoggingInterceptor()
        logger.level = HttpLoggingInterceptor.Level.BODY

        val loginInterceptor = LoginInterceptor(get())

        val client = OkHttpClient.Builder()
            .addInterceptor(ConnectivityInterceptor(androidContext()))
            .addInterceptor(SocketTimeOutInterceptor())
            .addInterceptor(loginInterceptor)
            .addInterceptor(logger)
            .build()

        val loginConfig: LoginConfig = get()
        val retrofit = Retrofit.Builder()
            .baseUrl(loginConfig.apiBaseUrl())
            .client(client)
            .addConverterFactory(GsonConverterFactory.create(get()))
            .build()

        retrofit.create<LoginService>(LoginService::class.java)
    }
    single { LoginRepository(get()) }
    viewModel { LoginViewModel(get()) }
}