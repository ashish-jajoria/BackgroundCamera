package `in`.backgroundcamera

import `in`.tflite.Classifier
import `in`.tflite.env.ImageUtils
import `in`.tflite.tracking.MultiBoxTracker
import `in`.tflite.utils.CameraPreviewAnalyzer
import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Bundle
import android.os.Handler
import android.util.Size
import android.view.Surface
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.android.synthetic.main.activity_main.*
import timber.log.Timber
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

typealias LumaListener = (luma: Double) -> Unit


@SuppressLint("RestrictedApi")
class MainActivity : AppCompatActivity() {

    companion object {
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS =
            arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
    }

    private lateinit var cameraProviderFuture : ListenableFuture<ProcessCameraProvider>
    private lateinit var cameraExecutor: ExecutorService
    private var cameraPreviewCallback:CameraPreviewAnalyzer? = null
    private lateinit var tracker:MultiBoxTracker
    private val detectionHandler = Handler()
    private val detectionRunnable = Runnable {
        trackingOverlay.visibility = View.GONE
    }

    private val objectListener = object: CameraPreviewAnalyzer.ObjectOfInterestListener {
        override fun onObjectDetected(
            label: String,
            confidence: Int,
            mappedRecognitions: MutableList<Classifier.Recognition>,
            currTimestamp: Long
        ) {
            Timber.e("Vehicle Detected")
            tracker.trackResults(mappedRecognitions, currTimestamp)
            trackingOverlay.postInvalidate()
        }

        override fun updateStatus(message: String) {
            status.text = message
        }

        override fun onObjectOfInterestDetected() {
            trackingOverlay.visibility = View.VISIBLE
            detectionHandler.postDelayed(detectionRunnable, 1_000)
        }

        override fun invalidateOverlay() {
            trackingOverlay.postInvalidate()
        }

        override fun addOverlayCallbacks(
            previewWidth: Int,
            previewHeight: Int,
            sensorOrientation: Int
        ) {
            tracker.setFrameConfiguration(previewWidth, previewHeight, sensorOrientation)
        }

        override fun onLPDetected(vehicleFile: File?, lpFile: File?, lpNumber: String?) {
            showDetectedVehicle(vehicleFile, lpFile, lpNumber)
        }

    }

    private fun showDetectedVehicle(vehicleFile: File?, lpFile: File?, lpNumber: String?) {
//        if(!lpNumber.isNullOrEmpty()) {
        var vehicle:Bitmap? = null
        if (vehicleFile == null) {
            vehicleImage.setImageBitmap(null)
        } else {
            vehicle = ImageUtils.getSavedBitmap("vehicle")
            vehicleImage.setImageBitmap(vehicle)
        }

        var lp:Bitmap? = null
        if (lpFile == null) {
            lpImage.setImageBitmap(null)
        } else {
            lp = ImageUtils.getSavedBitmap("lp_image")
            lpImage.setImageBitmap(lp)
        }

        lpNumberTv.text = lpNumber ?: ""

        if (vehicle != null && lp != null) {
            vehicleFile?.delete()
            lpFile?.delete()
            detectedVehicleLayout.visibility = View.VISIBLE
        } else {
//            detectedVehicleLayout.visibility = View.INVISIBLE
        }
//        }
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        tracker = MultiBoxTracker(this)

        shouldAnalyze.setOnCheckedChangeListener { button, _ ->  cameraPreviewCallback?.shouldAnalyze = button.isChecked}

        if (allPermissionsGranted()) {
            cameraProviderFuture = ProcessCameraProvider.getInstance(this)
            cameraProviderFuture.addListener(Runnable {
                val cameraProvider = cameraProviderFuture.get()
                bindPreview(cameraProvider)
            }, ContextCompat.getMainExecutor(this))
            cameraExecutor = Executors.newSingleThreadExecutor()
        } else {
            ActivityCompat.requestPermissions(
                this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS
            )
        }
    }

    private fun bindPreview(cameraProvider : ProcessCameraProvider) {
        val preview : Preview = Preview.Builder()
            .build()

        val cameraSelector : CameraSelector = CameraSelector.Builder()
            .requireLensFacing(CameraSelector.LENS_FACING_BACK)
            .build()

        val cameraControl = CameraX.getCameraWithCameraSelector(cameraSelector) // you can set it to front
        val meteringPoint = previewView.meteringPointFactory.createPoint(previewView.width/2f, previewView.height/2f, 1f)
        val action = FocusMeteringAction.Builder(meteringPoint).build()
        cameraControl.cameraControl.startFocusAndMetering(action)

        preview.setSurfaceProvider(previewView.createSurfaceProvider())

        trackingOverlay.addCallback { canvas -> tracker.draw(canvas) }

        cameraPreviewCallback = CameraPreviewAnalyzer(
            this,
            objectListener,
            getScreenOrientation()
        )

        val imageAnalyzer = ImageAnalysis.Builder()
            .setTargetResolution(Size(1920, 1080))
            .build()
            .also {
                cameraPreviewCallback?.let { analyzer ->
                    it.setAnalyzer(cameraExecutor, analyzer)
                }
            }

        cameraProvider.bindToLifecycle(this as LifecycleOwner, cameraSelector, preview,
            imageAnalyzer)
    }

    private fun getScreenOrientation():Int {
        return when (windowManager.defaultDisplay.rotation) {
            Surface . ROTATION_270 -> 270
            Surface . ROTATION_180 -> 180
            Surface . ROTATION_90 -> 90
            else -> 0
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults: IntArray
    ) {
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                cameraProviderFuture = ProcessCameraProvider.getInstance(this)
                cameraProviderFuture.addListener(Runnable {
                    val cameraProvider = cameraProviderFuture.get()
                    bindPreview(cameraProvider)
                }, ContextCompat.getMainExecutor(this))
            } else {
                Toast.makeText(
                    this,
                    "Permissions not granted by the user.",
                    Toast.LENGTH_SHORT
                ).show()
                finish()
            }
        }
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(
            baseContext, it
        ) == PackageManager.PERMISSION_GRANTED
    }
}
