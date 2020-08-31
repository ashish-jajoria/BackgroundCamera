package `in`.backgroundcamera

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.graphics.Matrix
import android.hardware.camera2.CameraManager
import android.os.Bundle
import android.os.CountDownTimer
import android.util.Rational
import android.util.Size
import android.view.Surface
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.android.synthetic.main.activity_main.*
import java.io.File


@SuppressLint("RestrictedApi")
class MainActivity : AppCompatActivity() {

    companion object {
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS =
            arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
    }

    var isRecording = false

    private lateinit var cameraProviderFuture : ListenableFuture<ProcessCameraProvider>

//    private val videoCaptureConfig = VideoCaptureConfig.Builder().setVideoFrameRate(30).build()
//    val videoCapture = VideoCapture(videoCaptureConfig)

//    private val timer = object : CountDownTimer(10000, 1000) {
//        override fun onTick(millisUntilFinished: Long) {
//            timerText.text = (millisUntilFinished / 1000).toString()
//        }
//
//        override fun onFinish() {
//            timerText.text = (0).toString()
//            videoCapture.stopRecording()
//        }
//    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        if (allPermissionsGranted()) {
            cameraProviderFuture = ProcessCameraProvider.getInstance(this)
            cameraProviderFuture.addListener(Runnable {
                val cameraProvider = cameraProviderFuture.get()
                bindPreview(cameraProvider)
            }, ContextCompat.getMainExecutor(this))
        } else {
            ActivityCompat.requestPermissions(
                this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS
            )
        }
    }

    fun bindPreview(cameraProvider : ProcessCameraProvider) {
        var preview : Preview = Preview.Builder()
            .build()

        var cameraSelector : CameraSelector = CameraSelector.Builder()
            .requireLensFacing(CameraSelector.LENS_FACING_BACK)
            .build()

        preview.setSurfaceProvider(previewView.createSurfaceProvider())

        var camera = cameraProvider.bindToLifecycle(this as LifecycleOwner, cameraSelector, preview)
    }

    /*private fun startCamera() {
        val previewConfig = PreviewConfig.Builder().apply {
            setTargetAspectRatio(Rational(1, 1))
            setTargetResolution(Size(640, 640))
        }.build()

        val preview = Preview(previewConfig)

        preview.setOnPreviewOutputUpdateListener {

            val parent = viewFinder.parent as ViewGroup
            parent.removeView(viewFinder)
            parent.addView(viewFinder, 0)

            viewFinder.setSurfaceTexture(it.surfaceTexture)
            updateTransform()
        }

        capture_button.setOnClickListener {
            timerText.text = ("").toString()
            if (isRecording) {
                isRecording = false
                videoCapture.stopRecording()
                timer.cancel()
                return@setOnClickListener
            }
            Toast.makeText(this, "Starting Recording", Toast.LENGTH_SHORT).show()
            isRecording = true
            startRecording(videoCapture)
            timer.start()
        }

        CameraX.bindToLifecycle(this, videoCapture)
    }*/

    /*private fun startRecording(videoCapture: VideoCapture) {
        val file = File(externalMediaDirs.first(), "${System.currentTimeMillis()}.mp4")
        videoCapture.startRecording(file,
            object : VideoCapture.OnVideoSavedListener {
                override fun onError(
                    error: VideoCapture.VideoCaptureError,
                    message: String,
                    exc: Throwable?
                ) {
                    val msg = "Photo capture failed: $message"
                    Toast.makeText(baseContext, msg, Toast.LENGTH_SHORT).show()
                    exc?.printStackTrace()
                }

                override fun onVideoSaved(file: File) {
                    val msg = "Photo capture succeeded: ${file.absolutePath}"
                    Toast.makeText(baseContext, msg, Toast.LENGTH_SHORT).show()
                    if (isRecording) {
                        timer.start()
                        startRecording(videoCapture)
                    }
                }
            })
    }

    private fun updateTransform() {
        val matrix = Matrix()

        // Compute the center of the view finder
        val centerX = viewFinder.width / 2f
        val centerY = viewFinder.height / 2f

        // Correct preview output to account for display rotation
        val rotationDegrees = when (viewFinder.display.rotation) {
            Surface.ROTATION_0 -> 0
            Surface.ROTATION_90 -> 90
            Surface.ROTATION_180 -> 180
            Surface.ROTATION_270 -> 270
            else -> return
        }
        matrix.postRotate(-rotationDegrees.toFloat(), centerX, centerY)

        // Finally, apply transformations to our TextureView
        viewFinder.setTransform(matrix)
    }*/

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
