package `in`.backgroundcamera

import `in`.api.responses.PocUserResponse
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.nayan.login.models.ActivityState
import co.nayan.login.models.ErrorState
import co.nayan.login.models.InitialState
import co.nayan.login.models.ProgressState
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.io.IOException

class UserViewModel(private val repository: UserRepository) : ViewModel() {

    private val _state: MutableLiveData<ActivityState> = MutableLiveData(InitialState)
    val state: LiveData<ActivityState> = _state

    fun isPocUser() {
        viewModelScope.launch {
            try {
                _state.value = ProgressState
                val response = repository.isPocUser()
                _state.value = PocUserSuccessState(response)
            } catch (e: HttpException) {
                _state.value = ErrorState(e)
            } catch (e: IOException) {
                _state.value = ErrorState(e)
            }
        }
    }

}

data class PocUserSuccessState(val pocUserResponse: PocUserResponse) : ActivityState()
