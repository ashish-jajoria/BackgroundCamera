package `in`.backgroundcamera

import `in`.api.BackgroundCameraService
import `in`.api.responses.PocUserResponse
import `in`.config.SharedStorage
import `in`.tflite.model.SubmitLpResponse
import co.nayan.login.models.User
import com.google.gson.Gson
import okhttp3.MultipartBody
import okhttp3.RequestBody
import java.util.*

class UserRepository(
    private val storage: SharedStorage,
    private val backgroundCameraService: BackgroundCameraService
) {

    fun setUserInfo(user: User) {
        storage.setUserProfileInfo(Gson().toJson(user))
        storage.setUserLoggedInStatus(true)
    }

    fun isUserLoggedIn(): Boolean {
        return storage.isUserLoggedIn()
    }

    suspend fun isPocUser(): PocUserResponse {
        return backgroundCameraService.isPocUser()
    }

    suspend fun readLp(
        hashMap: HashMap<String, RequestBody>, imageBodyPart: MultipartBody.Part
    ): SubmitLpResponse? {
        return backgroundCameraService.readLp(hashMap, imageBodyPart)
    }

    fun userLoggedOut() {
        storage.clearPreferences()
    }
}