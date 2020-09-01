@file:Suppress("DEPRECATION")

package `in`.tflite.utils

import `in`.backgroundcamera.toBitmap
import `in`.tflite.Classifier
import `in`.tflite.TFLiteLPDetectionAPIModel
import `in`.tflite.TFLiteVehicleDetectionAPIModel
import `in`.tflite.model.SubmitLpResponse
import android.content.Context
import android.graphics.*
import android.os.SystemClock
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import org.jetbrains.annotations.NotNull
import org.tensorflow.lite.gpu.GpuDelegate
import timber.log.Timber
import java.io.File
import java.nio.ByteBuffer
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt


class CameraPreviewAnalyzer(context: @NotNull Context,
                            private val listener: ObjectOfInterestListener,
                            private val screenRotation: Int) :
    ImageAnalysis.Analyzer {

    interface ObjectOfInterestListener {
        fun onObjectDetected(label: String, confidence: Int, mappedRecognitions: MutableList<Classifier.Recognition>, currTimestamp: Long)
        fun onObjectOfInterestDetected()
        fun invalidateOverlay()
        fun addOverlayCallbacks(previewWidth: Int, previewHeight: Int, sensorOrientation: Int)
        fun onLPDetected(vehicleFile: File, lpFile: File, lpNumber: String?)
    }

    private val client = OkHttpClient.Builder().callTimeout(10, TimeUnit.SECONDS).build()

    private var timestamp: Long = 0

    private var previewWidth = 0
    private var previewHeight = 0

    private var isProcessingFrame = false

    private var postInferenceCallback: Runnable? = null

    private var sensorOrientation: Int? = null
    private lateinit var rgbFrameBitmap: Bitmap
    private lateinit var croppedBitmap: Bitmap

    private var lastProcessingTimeMs: Long = 0

    private var frameToCropTransform: Matrix? = null
    private var cropToFrameTransform: Matrix? = Matrix()

    private var vehicleClassifier: Classifier = TFLiteVehicleDetectionAPIModel.create(
            context.assets,
            TF_OD_API_VEHICLE_MODEL_FILE,
            TF_OD_API_LABELS_FILE,
            TF_OD_API_INPUT_SIZE,
            TF_OD_API_IS_QUANTIZED, GpuDelegate())

    private var lpClassifier: Classifier = TFLiteLPDetectionAPIModel.create(
            context.assets,
            TF_OD_API_LP_MODEL_FILE,
            TF_OD_API_LABELS_FILE,
            TF_OD_API_INPUT_SIZE,
            false)


    private fun ByteBuffer.toByteArray(): ByteArray {
        rewind()    // Rewind the buffer to zero
        val data = ByteArray(remaining())
        get(data)   // Copy the buffer into a byte array
        return data // Return the byte array
    }

    override fun analyze(image: ImageProxy) {
        ++timestamp
        val currTimestamp = timestamp
        listener.invalidateOverlay()

        if (isProcessingFrame) {
            Timber.d("Dropping frame!")
            image.close()
            return
        }

        try {
            // Initialize the storage bitmaps once when the resolution is known.
            previewHeight = image.height
            previewWidth = image.width
            onPreviewSizeChosen(Size(previewWidth, previewHeight), 90)
        } catch (e: Exception) {
            Timber.e(e)
            image.close()
            return
        }

        val bitmap = image.image?.toBitmap()

        isProcessingFrame = true
        image.close()

        postInferenceCallback = Runnable {
            isProcessingFrame = false
        }

        bitmap?.let {bmp ->
            processImage(currTimestamp, bmp)
        }
    }

    private fun processImage(currTimestamp: Long, bitmap: Bitmap) {
        rgbFrameBitmap = bitmap
        val mainCanvas = Canvas(croppedBitmap)
        frameToCropTransform?.let {
            mainCanvas.drawBitmap(rgbFrameBitmap, it, null)
        }

        Timber.d("start time: ${System.currentTimeMillis()}")
        GlobalScope.launch {
            var vehicleFile: File? = null
            var lpFile: File? = null
            val results = withContext(Dispatchers.IO) {
                val startTime = SystemClock.uptimeMillis()
                val results = vehicleClassifier.recognizeImage(croppedBitmap)
                lastProcessingTimeMs = SystemClock.uptimeMillis() - startTime
                Timber.e(String.format("Detect: %s", results))
                Timber.e("Time Taken $lastProcessingTimeMs")
                results
            }

            val cropCopyBitmap = Bitmap.createBitmap(croppedBitmap)
            val canvas = Canvas(cropCopyBitmap)
            val paint = Paint()
            paint.color = Color.RED
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 2.0f

            val mappedRecognitions = mutableListOf<Classifier.Recognition>()

            for (result in results) {
                val location = result.location
                if (location != null && result.confidence >= MINIMUM_CONFIDENCE_SCORE) {
                    result.location?.let { rect ->
                        val recognizedBitmap =
                                getRecognizedBitmap(Bitmap.createBitmap(croppedBitmap), rect)

                        recognizedBitmap?.let {
                            val lpResults = withContext(Dispatchers.IO) {
                                val startTime = SystemClock.uptimeMillis()
                                val lpResults = lpClassifier.recognizeImage(recognizedBitmap)
                                lastProcessingTimeMs = SystemClock.uptimeMillis() - startTime
                                Timber.d(String.format("Detect: %s", results))
                                Timber.e("Time Taken LP $lastProcessingTimeMs")
                                lpResults
                            }

                            if (lpResults.isNullOrEmpty()) {
                                result.confidence = 0f
                            } else {
                                val maxResult = lpResults.first()
                                if (isValidLp(maxResult.location)) {
                                    vehicleFile = ImageUtils.saveDetectedImage(it, "vehicle.jpg")
                                    canvas.drawRect(location, paint)
                                    cropToFrameTransform!!.mapRect(location)
                                    result.location = location
                                    mappedRecognitions.add(result)

                                    val lpLocation = getLpPoints(maxResult.location, rect)
                                    canvas.drawRect(lpLocation, paint)
                                    cropToFrameTransform!!.mapRect(lpLocation)
                                    maxResult.location = lpLocation

                                    val lpImage = getLpImage(lpLocation)
                                    lpFile = ImageUtils.saveDetectedImage(lpImage, "lp_image.jpg")
                                    mappedRecognitions.add(maxResult)
                                } else {
                                    result.confidence = 0f
                                }
                            }
                        }
                    }
                }
            }

            vehicleFile?.let { f1 ->
                lpFile?.let { f2 ->
                    val lpResult = uploadLpImage(f2)
                    withContext(Dispatchers.Main) {
                        listener.onLPDetected(f1, f2, lpResult)
                    }
                }
            }

            Timber.d("end time: ${System.currentTimeMillis()}")


            if (isShowingCar(results)) {
                Timber.e("Vehicle Detected from callback")
                val maxResult = results.maxBy { it.confidence }

                maxResult?.let {
                    withContext(Dispatchers.Main) {
                        listener.onObjectDetected(
                            it.title,
                            (it.confidence * 100).roundToInt(),
                            mappedRecognitions,
                            currTimestamp
                        )
                        listener.onObjectOfInterestDetected()
                    }
                }
            }

            readyForNextImage()
        }
    }

    private fun isValidLp(location: RectF): Boolean {
        return location.width() * location.height() > 200
    }

    private fun getLpImage(lpLocation: RectF): Bitmap? {
        return try {
            Bitmap.createBitmap(rgbFrameBitmap, lpLocation.left.toInt(), lpLocation.top.toInt(), lpLocation.width().toInt(), lpLocation.height().toInt())
        } catch (e: IllegalArgumentException) {
            null
        }
    }

    private suspend fun uploadLpImage(lpFile: File): String? = withContext(Dispatchers.IO) {
            val uploadUrl = "http://35.229.238.216/ocr/read_lp"

            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("image", lpFile.name, lpFile.asRequestBody())
                .build()

            val request = Request.Builder().url(uploadUrl).addHeader("api-key", "v4RjT5ZwLTWGH2xHceTLH8w5").post(requestBody).build()

            val response = client.newCall(request).execute()

            if (response.isSuccessful) {
                val responseBody = response.body
                if (responseBody == null) {
                    Timber.e("Upload LP:: Could not upload ${lpFile.name}")
                    return@withContext null
                } else {
                    val lpResponse = Gson().fromJson(responseBody.string(), SubmitLpResponse::class.java)
                    Timber.d("LP Number ${lpResponse.result} (${lpResponse.result})")
                    val lpResult = lpResponse.result
                    if (lpResult.isNullOrEmpty()) {
                        return@withContext null
                    } else {
                        return@withContext lpResult
                    }
                }
            } else {
                Timber.e("Upload LP:: Could not upload ${lpFile.name}")
                return@withContext null
            }
    }

    private fun getLpPoints(location: RectF, rect: RectF): RectF {
        val left = rect.left + location.left
        val top = rect.top + location.top
        val bottom = top + location.height()
        val right = left + location.width()

        return RectF(left, top, right, bottom)
    }

    private fun getRecognizedBitmap(croppedBitmap: Bitmap, rectF: RectF): Bitmap? {
        return try {
            Bitmap.createBitmap(croppedBitmap, rectF.left.toInt(), rectF.top.toInt(), rectF.width().toInt(), rectF.height().toInt())
        } catch (e: IllegalArgumentException) {
            null
        }
    }

    private fun readyForNextImage() {
        postInferenceCallback?.run()
    }

    @Suppress("SameParameterValue")
    private fun onPreviewSizeChosen(size: Size, rotation: Int) {
        previewWidth = size.width
        previewHeight = size.height

        sensorOrientation = rotation - screenRotation

        croppedBitmap = Bitmap.createBitmap(
                TF_OD_API_INPUT_SIZE, TF_OD_API_INPUT_SIZE, Bitmap.Config.ARGB_8888)

        frameToCropTransform = ImageUtils.getTransformationMatrix(
                previewWidth,
                previewHeight,
                TF_OD_API_INPUT_SIZE,
                TF_OD_API_INPUT_SIZE,
                sensorOrientation!!,
                MAINTAIN_ASPECT)

        frameToCropTransform?.invert(cropToFrameTransform)

        listener.addOverlayCallbacks(previewWidth, previewHeight, sensorOrientation!!)
    }

    private fun isShowingCar(predictions: List<Classifier.Recognition>): Boolean {
        return predictions.any {
            it.confidence > 0.7
        }
    }

    companion object {
        private const val TF_OD_API_INPUT_SIZE = 300
        private const val TF_OD_API_IS_QUANTIZED = true
        private const val TF_OD_API_VEHICLE_MODEL_FILE = "detect_v_1-0-3.tflite"
        private const val TF_OD_API_LP_MODEL_FILE = "LP_detection_v1.0.0.1.tflite"
        private const val TF_OD_API_LABELS_FILE = "file:///android_asset/labelmap1.txt"
        private const val MINIMUM_CONFIDENCE_SCORE = 0.5f

        private const val MAINTAIN_ASPECT = false


    }

    data class Size(val width: Int, val height: Int)
}