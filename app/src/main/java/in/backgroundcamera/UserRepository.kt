package `in`.backgroundcamera

import `in`.api.BackgroundCameraService
import `in`.api.responses.PocUserResponse
import `in`.config.SharedStorage
import co.nayan.login.models.User
import com.google.gson.Gson

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
}