package co.nayan.login

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.nayan.login.models.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import java.io.IOException

class LoginViewModel(private val repository: LoginRepository) : ViewModel() {

    private val _state: MutableLiveData<ActivityState> = MutableLiveData(
        InitialState
    )
    val state: LiveData<ActivityState> = _state

    fun login(username: String, password: String) {
        viewModelScope.launch {
            try {
                _state.value = ProgressState
                val user = doLogin(username, password)
                _state.value = LoginSuccessState(user)
            } catch (e: HttpException) {
                _state.value = ErrorState(e)
            } catch (e: IOException) {
                _state.value = ErrorState(e)
            }
        }
    }

    private suspend fun doLogin(username: String, password: String): User =
        withContext(Dispatchers.IO) {
            repository.login(username, password)
        }

    data class LoginSuccessState(val user: User) : ActivityState()
}