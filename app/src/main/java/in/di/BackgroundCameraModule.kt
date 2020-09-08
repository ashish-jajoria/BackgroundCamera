package `in`.di

import `in`.api.BackgroundCameraService
import `in`.backgroundcamera.UserRepository
import `in`.config.*
import co.nayan.login.config.AuthManager
import co.nayan.login.config.ConnectivityInterceptor
import co.nayan.login.config.LoginConfig
import co.nayan.login.config.SocketTimeOutInterceptor
import com.google.gson.FieldNamingPolicy
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

val backgroundCameraModule = module {

    single<Gson> {
        val gsonBuilder = GsonBuilder()
        gsonBuilder.setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
        gsonBuilder.create()
    }

    single { SharedStorage(androidContext()) }
    single<AuthManager> { AuthManagerImpl(get()) }
    single<LoginConfig> { LoginConfigImpl(get()) }
    single { HeadersInterceptor(get(), androidContext()) }

    single<BackgroundCameraService> {

        val logger = HttpLoggingInterceptor()
        logger.level = HttpLoggingInterceptor.Level.BODY

        val headersInterceptor: HeadersInterceptor = get()

        val client = OkHttpClient.Builder()
            .addInterceptor(ConnectivityInterceptor(androidContext()))
            .addInterceptor(SocketTimeOutInterceptor())
            .addInterceptor(headersInterceptor)
            .addInterceptor(logger)
            .build()

        val retrofit = Retrofit.Builder()
            .baseUrl(Constants.BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create(get()))
            .build()

        retrofit.create<BackgroundCameraService>(BackgroundCameraService::class.java)
    }

    single { UserRepository(get(), get()) }
}