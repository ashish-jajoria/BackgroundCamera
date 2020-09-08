package co.nayan.login.api

import co.nayan.login.models.LoginRequest
import co.nayan.login.models.LoginResponse
import retrofit2.http.Body
import retrofit2.http.POST

interface LoginService {

    @POST("/auth/sign_in")
    suspend fun login(@Body request: LoginRequest): LoginResponse
}