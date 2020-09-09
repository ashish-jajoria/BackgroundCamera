package `in`.api

import `in`.api.responses.PocUserResponse
import `in`.tflite.model.SubmitLpResponse
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.http.*

interface BackgroundCameraService {

    @GET("/ocr/is_allowed")
    suspend fun isPocUser(): PocUserResponse

    @Multipart
    @POST("/ocr/read_lp")
    suspend fun readLp(
        @PartMap requestCode: HashMap<String, RequestBody>,
        @Part image: MultipartBody.Part
    ): SubmitLpResponse
}