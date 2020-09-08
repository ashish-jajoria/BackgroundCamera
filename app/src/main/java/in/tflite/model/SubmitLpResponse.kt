package `in`.tflite.model

data class SubmitLpResponse(
        val success: Boolean?,
        val result: String?,
        val show_alert: Boolean? = false,
        val request_code: String
)

