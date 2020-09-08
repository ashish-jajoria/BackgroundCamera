package `in`.backgroundcamera

import `in`.tflite.Classifier
import `in`.tflite.env.ImageUtils
import `in`.tflite.model.DetectedVehicle
import `in`.tflite.tracking.MultiBoxTracker
import `in`.tflite.utils.CameraPreviewAnalyzer
import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
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
import androidx.core.view.isVisible
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.Observer
import co.nayan.login.LoginActivity
import co.nayan.login.models.*
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.android.synthetic.main.activity_main.*
import org.koin.android.ext.android.inject
import org.koin.android.viewmodel.ext.android.viewModel
import timber.log.Timber
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

typealias LumaListener = (luma: Double) -> Unit


@SuppressLint("RestrictedApi")
class MainActivity : AppCompatActivity() {

    private val userRepository: UserRepository by inject()
    private val userViewModel: UserViewModel by viewModel()

    companion object {
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS =
            arrayOf(
                Manifest.permission.CAMERA,
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                Manifest.permission.READ_EXTERNAL_STORAGE
            )
    }

    private lateinit var cameraProviderFuture: ListenableFuture<ProcessCameraProvider>
    private lateinit var cameraExecutor: ExecutorService
    private var cameraPreviewCallback: CameraPreviewAnalyzer? = null
    private lateinit var tracker: MultiBoxTracker
    var isShowingAlert: Boolean = false
    private var detectedVehicles = mutableListOf<DetectedVehicle>()
    private val detectionHandler = Handler()
    private val detectionRunnable = Runnable {
        trackingOverlay.visibility = View.GONE
    }

    private val objectListener = object : CameraPreviewAnalyzer.ObjectOfInterestListener {
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

        override fun onLpNumberDetected(detectedVehicle: DetectedVehicle) {
            if (detectedVehicle.alert) {
                detectedVehicles.add(detectedVehicle)

                if (isShowingAlert) {
                    return
                }
                val firstVehicle = detectedVehicles.first()
                detectedVehicles.removeFirst()
                showDetectedVehicle(firstVehicle)
            } else {
                if (isShowingAlert) {
                    return
                } else {
                    showDetectedVehicle(detectedVehicle)
                }
            }
        }

        override fun onVehicleAndLpDetected(vehicleFile: Bitmap?, lpFile: Bitmap?) {
            showDetectedLiveVehicle(vehicleFile, lpFile)
        }
    }

    private fun showDetectedLiveVehicle(vehicleBitmap: Bitmap?, lpBitmap: Bitmap?) {
        liveVehicleImage.setImageBitmap(vehicleBitmap)
        liveLpImage.setImageBitmap(lpBitmap)

        if (vehicleBitmap == null && lpBitmap == null) {
            liveVehicleLayout.visibility = View.INVISIBLE
        } else {
            liveVehicleLayout.visibility = View.VISIBLE
        }
    }

    private fun showDetectedVehicle(detectedVehicle: DetectedVehicle) {
        isShowingAlert = detectedVehicle.alert
        if (!detectedVehicle.lpNumber.isNullOrEmpty()) {
            var vehicle: Bitmap? = null
            if (detectedVehicle.vehicleFile == null) {
                vehicleImage.setImageBitmap(null)
            } else {
                vehicle = ImageUtils.getSavedBitmap("${detectedVehicle.uuid}_vehicle")
                vehicleImage.setImageBitmap(vehicle)
            }

            var lp: Bitmap? = null
            if (detectedVehicle.lpFile == null) {
                lpImage.setImageBitmap(null)
            } else {
                lp = ImageUtils.getSavedBitmap("${detectedVehicle.uuid}_lp_image")
                lpImage.setImageBitmap(lp)
            }

            lpNumberTv.text = detectedVehicle.lpNumber

            if (vehicle != null && lp != null) {
                detectedVehicle.vehicleFile?.delete()
                detectedVehicle.lpFile?.delete()
                detectedVehicleLayout.visibility = View.VISIBLE
            } else {
                detectedVehicleLayout.visibility = View.INVISIBLE
            }

            if (detectedVehicle.alert) {
                detectedVehicleLayout.setBackgroundColor(Color.parseColor("#FF0000"));
                SoundUtils.playSound("lp_alert", this);
            } else {
                detectedVehicleLayout.setBackgroundColor(Color.parseColor("#EEEEEE"));
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.AppTheme)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        intent.getParcelableExtra<User>("user")?.let { userRepository.setUserInfo(it) }

        userViewModel.state.observe(this, stateObserver)

        if (userRepository.isUserLoggedIn()) {
            userViewModel.isPocUser()
        } else {
            loggedOutUser()
        }
    }

    private fun loggedOutUser() {
        Intent(this@MainActivity, LoginActivity::class.java).apply {
            this.putExtra(LoginActivity.VERSION_NAME, BuildConfig.VERSION_NAME)
            this.putExtra(LoginActivity.VERSION_CODE, BuildConfig.VERSION_CODE)
            startActivity(this)
            finish()
        }
    }

    private val stateObserver: Observer<ActivityState> = Observer {
        when (it) {
            ProgressState -> {
               progressBar.visibility = View.VISIBLE
            }
            is PocUserSuccessState -> {
                progressBar.visibility = View.GONE
                if(it.pocUserResponse.isAllowed == true) {
                    tracker = MultiBoxTracker(this)

                    shouldAnalyze.setOnCheckedChangeListener { button, _ ->
                        cameraPreviewCallback?.shouldAnalyze = button.isChecked
                    }

                    if (allPermissionsGranted()) {
                        initializeCameraProperties()
                    } else {
                        ActivityCompat.requestPermissions(
                            this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS
                        )
                    }

                    detectedVehicleLayout.setOnClickListener {
                        if (isShowingAlert) {
                            isShowingAlert = false
                            if (detectedVehicles.isNotEmpty()) {
                                showDetectedVehicle(detectedVehicles.first())
                                detectedVehicles.removeFirst()
                                return@setOnClickListener
                            }
                        }

                        if (detectedVehicleLayout.isVisible) {
                            detectedVehicleLayout.visibility = View.INVISIBLE
                        }
                    }
                } else {
                    loggedOutUser()
                }
            }
            is ErrorState -> {
                finish()
            }
        }
    }

    private fun bindPreview(cameraProvider: ProcessCameraProvider) {
        val preview: Preview = Preview.Builder()
            .build()

        val cameraSelector: CameraSelector = CameraSelector.Builder()
            .requireLensFacing(CameraSelector.LENS_FACING_BACK)
            .build()

        val cameraControl =
            CameraX.getCameraWithCameraSelector(cameraSelector) // you can set it to front
        val meteringPoint = previewView.meteringPointFactory.createPoint(
            previewView.width / 2f,
            previewView.height / 2f,
            1f
        )
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

        cameraProvider.bindToLifecycle(
            this as LifecycleOwner, cameraSelector, preview,
            imageAnalyzer
        )
    }

    private fun getScreenOrientation(): Int {
        return when (windowManager.defaultDisplay.rotation) {
            Surface.ROTATION_270 -> 270
            Surface.ROTATION_180 -> 180
            Surface.ROTATION_90 -> 90
            else -> 0
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if(this:: cameraExecutor.isInitialized) {
            cameraExecutor.shutdown()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults: IntArray
    ) {
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                initializeCameraProperties()
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

    private fun initializeCameraProperties() {
        cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            bindPreview(cameraProvider)
        }, ContextCompat.getMainExecutor(this))
        cameraExecutor = Executors.newSingleThreadExecutor()
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(
            baseContext, it
        ) == PackageManager.PERMISSION_GRANTED
    }
}
