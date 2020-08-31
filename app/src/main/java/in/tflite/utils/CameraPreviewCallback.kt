@file:Suppress("DEPRECATION")

package com.nayan.nayanindia.tflite.utils;

import android.content.Context
import android.graphics.*
import android.hardware.Camera
import android.os.SystemClock
import android.util.Log
import com.google.gson.Gson
import com.loopj.android.http.AsyncHttpResponseHandler
import com.loopj.android.http.RequestParams
import com.loopj.android.http.SyncHttpClient
import com.nayan.nayanindia.models.SubmitLpResponse
import com.nayan.nayanindia.tflite.Classifier
import com.nayan.nayanindia.tflite.TFLiteLPDetectionAPIModel
import com.nayan.nayanindia.tflite.TFLiteVehicleDetectionAPIModel
import cz.msebera.android.httpclient.Header
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.apache.commons.io.IOUtils
import org.jetbrains.annotations.NotNull
import timber.log.Timber
import java.io.File


class CameraPreviewCallback(context: @NotNull Context,
                            private val listener: @NotNull ObjectOfInterestListener,
                            private val screenRotation: Int) :
        Camera.PreviewCallback {

    interface ObjectOfInterestListener {
        fun onObjectDetected(label: String, confidence: Int, mappedRecognitions: MutableList<Classifier.Recognition>, currTimestamp: Long)
        fun onObjectOfInterestDetected()
        fun invalidateOverlay()
        fun addOverlayCallbacks(previewWidth: Int, previewHeight: Int, sensorOrientation: Int)
        fun onLPDetected(vehicleFile: File, lpFile: File, lpNumber: String?)
    }

    private var timestamp: Long = 0

    private var previewWidth = 0
    private var previewHeight = 0

    private var isProcessingFrame = false

    private var rgbBytes: IntArray? = null
    private val yuvBytes = arrayOfNulls<ByteArray>(3)
    private var yRowStride: Int = 0

    private var imageConverter: Runnable? = null
    private var postInferenceCallback: Runnable? = null

    private var sensorOrientation: Int? = null
    private lateinit var rgbFrameBitmap: Bitmap
    private lateinit var croppedBitmap: Bitmap

    private var lastProcessingTimeMs: Long = 0

    private var frameToCropTransform: Matrix? = null
    private var cropToFrameTransform: Matrix? = null

    private var frameIndex: Int = 0

    private var computingDetection: Boolean = false

    private var vehicleClassifier: Classifier = TFLiteVehicleDetectionAPIModel.create(
            context.assets,
            TF_OD_API_VEHICLE_MODEL_FILE,
            TF_OD_API_LABELS_FILE,
            TF_OD_API_INPUT_SIZE,
            TF_OD_API_IS_QUANTIZED)

    private var lpClassifier: Classifier = TFLiteLPDetectionAPIModel.create(
            context.assets,
            TF_OD_API_LP_MODEL_FILE,
            TF_OD_API_LABELS_FILE,
            TF_OD_API_INPUT_SIZE,
            false)

    private var canDetect: Boolean = true

    override fun onPreviewFrame(bytes: ByteArray, camera: Camera) {
        ++timestamp
        val currTimestamp = timestamp
        listener.invalidateOverlay()

        /* if (canDetect) {*/
        // Process every 10th Frame
        frameIndex += 1
        if (frameIndex % 10 == 0) {
            if (isProcessingFrame) {
                Log.w(TAG, "Dropping frame!")
                return
            }

            try {
                // Initialize the storage bitmaps once when the resolution is known.
                if (rgbBytes == null) {
                    val previewSize = camera.parameters.previewSize
                    previewHeight = previewSize.height
                    previewWidth = previewSize.width
                    rgbBytes = IntArray(previewWidth * previewHeight)
                    onPreviewSizeChosen(Size(previewSize.width, previewSize.height), 90)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Exception!", e)
                return
            }

            isProcessingFrame = true
            yuvBytes[0] = bytes
            yRowStride = previewWidth

            imageConverter = Runnable {
                ImageUtils.convertYUV420SPToARGB8888(bytes, previewWidth, previewHeight, rgbBytes)
            }

            postInferenceCallback = Runnable {
                camera.addCallbackBuffer(bytes)
                isProcessingFrame = false
            }

            processImage(currTimestamp)
        }
        //}
    }

    private fun processImage(currTimestamp: Long) {
        rgbFrameBitmap.setPixels(getRgbBytes(), 0, previewWidth, 0, 0, previewWidth, previewHeight)
        val canvas = Canvas(croppedBitmap)
        canvas.drawBitmap(rgbFrameBitmap, frameToCropTransform!!, null)

        Log.d("start time:", System.currentTimeMillis().toString())
        //ImageUtils.saveBitmap(croppedBitmap, currTimestamp)
        GlobalScope.launch {
            var maxConfidence = 0.0f
            var vehicleFile: File? = null
            var lpFile: File? = null
            val results = withContext(Dispatchers.IO) {
                val startTime = SystemClock.uptimeMillis()
                val results = vehicleClassifier.recognizeImage(croppedBitmap)
                lastProcessingTimeMs = SystemClock.uptimeMillis() - startTime
                Log.v(TAG, String.format("Detect: %s", results))
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
                                Log.v(TAG, String.format("Detect: %s", results))
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
                    uploadLpImage(f1, f2)
                }
            }

            Log.d("end time:", System.currentTimeMillis().toString())

            val maxResult = results.maxBy { it.confidence }
            maxResult?.let {
                listener.onObjectDetected(
                        it.title,
                        (it.confidence * 100).roundToInt(),
                        mappedRecognitions,
                        currTimestamp
                )
            }

            if (isShowingCar(results) && vehicleFile != null && lpFile != null) {
                withContext(Dispatchers.Main) {
                    listener.onObjectOfInterestDetected()
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

    private suspend fun uploadLpImage(vehicleFile: File, lpFile: File): Boolean = withContext(Dispatchers.IO) {
        try {
            val uploadUrl = "http://35.229.238.216/ocr/read_lp"
            val params = RequestParams()
            params.put("image", lpFile)
            val client = SyncHttpClient()
            client.responseTimeout = 10 * 60 * 1000
            client.connectTimeout = 10 * 60 * 1000
            client.addHeader("api-key", "v4RjT5ZwLTWGH2xHceTLH8w5")

            var success = true

            client.post(uploadUrl, params, object : AsyncHttpResponseHandler() {
                override fun onSuccess(statusCode: Int, headers: Array<out Header>?,
                                       responseBody: ByteArray?) {
                    val response = IOUtils.toString(responseBody)
                    val lpResponse = Gson().fromJson<SubmitLpResponse>(response, SubmitLpResponse::class.java)
                    Timber.d("LP Number ${lpResponse.result}" + "( ${lpResponse.result})")
                    success = true
                    val lpResult = lpResponse.result
                    if (!lpResult.isNullOrEmpty()) {
                        listener.onLPDetected(vehicleFile, lpFile, lpResult)
                    }
                }

                override fun onFailure(statusCode: Int,
                                       headers: Array<Header>,
                                       responseBody: ByteArray, error: Throwable) {
                    success = false
                    Timber.e(error, "Upload LP:: Could no upload ${lpFile.name}")
                }
            })
            success
        } catch (e: Exception) {
            Timber.d("Could not upload file")
            false
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

    private fun isShowingCar(predictions: List<Classifier.Recognition>): Boolean {
        return predictions.any {
            it.confidence > 0.7
        }
    }

    private fun getRgbBytes(): IntArray? {
        imageConverter?.run()
        return rgbBytes
    }

    private fun readyForNextImage() {
        postInferenceCallback?.run()
    }

    @Suppress("SameParameterValue")
    private fun onPreviewSizeChosen(size: Size, rotation: Int) {
        previewWidth = size.width
        previewHeight = size.height

        sensorOrientation = rotation - screenRotation

        rgbFrameBitmap = Bitmap.createBitmap(previewWidth, previewHeight, Bitmap.Config.ARGB_8888)
        croppedBitmap = Bitmap.createBitmap(
                TF_OD_API_INPUT_SIZE, TF_OD_API_INPUT_SIZE, Bitmap.Config.ARGB_8888)

        frameToCropTransform = ImageUtils.getTransformationMatrix(
                previewWidth,
                previewHeight,
                TF_OD_API_INPUT_SIZE,
                TF_OD_API_INPUT_SIZE,
                sensorOrientation!!,
                MAINTAIN_ASPECT)

        cropToFrameTransform = Matrix()
        frameToCropTransform?.invert(cropToFrameTransform)

        listener.addOverlayCallbacks(previewWidth, previewHeight, sensorOrientation!!)
    }

    fun canDetectObjects(toSet: Boolean) {
        canDetect = toSet
    }

    companion object {
        private const val TF_OD_API_INPUT_SIZE = 300
        private const val TF_OD_API_IS_QUANTIZED = true
        private const val TF_OD_API_VEHICLE_MODEL_FILE = "detect_v_1-0-3.tflite"
        private const val TF_OD_API_LP_MODEL_FILE = "LP_detection_v1.0.0.1.tflite"
        private const val TF_OD_API_LABELS_FILE = "file:///android_asset/labelmap1.txt"
        private const val MINIMUM_CONFIDENCE_SCORE = 0.5f

        private const val TAG = "CameraPreviewCallback"
        private const val MAINTAIN_ASPECT = false


    }

    data class Size(val width: Int, val height: Int)
}