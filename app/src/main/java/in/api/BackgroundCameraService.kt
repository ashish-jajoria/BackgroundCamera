package `in`.api

import co.nayan.login.models.LoginRequest
import co.nayan.login.models.LoginResponse
import retrofit2.http.Body
import retrofit2.http.POST

interface BackgroundCameraService {

    @POST("/auth/sign_in")
    suspend fun login(@Body request: LoginRequest): LoginResponse
}