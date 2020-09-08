package co.nayan.login.models

open class ActivityState

object InitialState : ActivityState()
object ProgressState : ActivityState()
object SuccessState : ActivityState()
data class ErrorState(val exception: Exception) : ActivityState()