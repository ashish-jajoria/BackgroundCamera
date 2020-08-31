package `in`.tflite.model

data class LpProbs(
        val prob: Float,
        val tlX: Float,
        val tlY: Float,
        var brX: Float,
        var brY: Float
)