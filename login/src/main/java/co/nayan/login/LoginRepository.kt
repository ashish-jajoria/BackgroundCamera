package co.nayan.login

import co.nayan.login.api.LoginService
import co.nayan.login.models.LoginRequest
import co.nayan.login.models.User

class LoginRepository(private val service: LoginService) {

    suspend fun login(username: String, password: String): User {
        return service.login(LoginRequest(username, password)).data
    }
}