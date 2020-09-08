package `in`.tflite.model

import java.io.File
import java.util.*

class DetectedVehicle(val vehicleFile: File?, val lpFile: File?, val lpNumber: String?, val alert: Boolean, val uuid: UUID)