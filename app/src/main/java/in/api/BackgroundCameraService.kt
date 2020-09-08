package `in`.api

import `in`.api.responses.PocUserResponse
import retrofit2.http.GET

interface BackgroundCameraService {

    @GET("/users/is_poc_user")
    suspend fun isPocUser(): PocUserResponse
}