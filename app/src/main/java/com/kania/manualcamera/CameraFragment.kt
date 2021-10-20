package com.kania.manualcamera

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Camera
import android.graphics.ImageFormat
import android.hardware.camera2.*
import android.media.Image
import android.media.ImageReader
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.view.*
import android.widget.*
import androidx.annotation.RequiresApi
import androidx.exifinterface.media.ExifInterface
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.lifecycleScope
import com.kania.manualcamera.databinding.FragmentCameraBinding
import com.kania.manualcamera.databinding.LayoutModeControlBinding
import com.kania.manualcamera.databinding.LayoutRangeControlBinding
import kotlinx.coroutines.*
import java.io.Closeable
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.lang.Runnable
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeoutException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine
import kotlin.properties.Delegates

// TODO: Rename parameter arguments, choose names that match
// the fragment initialization parameters, e.g. ARG_ITEM_NUMBER
private const val ARG_PARAM1 = "cameraId"
private const val ARG_PARAM2 = "param2"

/**
 * A simple [Fragment] subclass.
 * Use the [CameraFragment.newInstance] factory method to
 * create an instance of this fragment.
 */
class CameraFragment : Fragment() {

    // TODO: Rename and change types of parameters
    private var param1: String? = null
    private var param2: String? = null

    //TODO temp id
    private var cameraId = "0"
    private var imageFormat = ImageFormat.JPEG

    //TODO error handling when exit cameraFragment
    private var _fragmentCameraBinding: FragmentCameraBinding? = null
    private val fragmentCameraBinding get() = _fragmentCameraBinding!!

    private val cameraManager: CameraManager by lazy {
        val context = requireContext().applicationContext
        context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    }

    private val characteristics: CameraCharacteristics by lazy {
        cameraManager.getCameraCharacteristics(cameraId)
    }

    private lateinit var imageReader: ImageReader

    private val cameraThread = HandlerThread("CameraThread").apply { start() }

    private val cameraHandler = Handler(cameraThread.looper)

    private val imageReaderThread = HandlerThread("imageReaderThread").apply { start() }

    private val imageReaderHandler = Handler(imageReaderThread.looper)

    private lateinit var camera: CameraDevice

    private lateinit var session: CameraCaptureSession

    private lateinit var relativeOrientation: OrientationLiveData

    private val initialCaptureCallback = object : CameraCaptureSession.CaptureCallback() {
        override fun onCaptureCompleted(
            session: CameraCaptureSession,
            request: CaptureRequest,
            result: TotalCaptureResult
        ) {
            handleInitialCaptureRequest(result)
        }
    }

    private val captureCallback = object : CameraCaptureSession.CaptureCallback() {
        override fun onCaptureCompleted(
            session: CameraCaptureSession,
            request: CaptureRequest,
            result: TotalCaptureResult
        ) {
            handleCaptureResult(result)
        }
    }

    //values
    private var isValueSet = false

    //CONTROL_MODE
    private val modes = mapOf(
        CameraCharacteristics.CONTROL_MODE_OFF to "OFF",
        CameraCharacteristics.CONTROL_MODE_AUTO to "AUTO",
        CameraCharacteristics.CONTROL_MODE_USE_SCENE_MODE to "USE_SCENE_MODE",
        CameraCharacteristics.CONTROL_MODE_OFF_KEEP_STATE to "OFF_KEEP_STATE",
        //CameraCharacteristics.CONTROL_MODE_USE_EXTENDED_SCENE_MODE to "EXTENDED_SCENE_MODE",
    )
    private var modeValue: Int by Delegates.observable(-1) { _, oldValue, newValue ->
        if (oldValue != newValue) {
            fragmentCameraBinding.run {
                controlMode.textCurrent.text = modes[newValue]
            }
        }
    }
    private var modeControl: Int = CameraCharacteristics.CONTROL_MODE_OFF

    //CONTROL_AE_MODE
    private val aeModes = mapOf(
        CameraCharacteristics.CONTROL_AE_MODE_OFF to "OFF",
        CameraCharacteristics.CONTROL_AE_MODE_ON to "ON",
        CameraCharacteristics.CONTROL_AE_MODE_ON_AUTO_FLASH to "ON_AUTO_FLASH",
        CameraCharacteristics.CONTROL_AE_MODE_ON_ALWAYS_FLASH to "ON_ALWAYS_FLASH",
        CameraCharacteristics.CONTROL_AE_MODE_ON_AUTO_FLASH_REDEYE to "ON_AUTO_FLASH_REDEYE",
        //CameraCharacteristics.CONTROL_AE_MODE_ON_EXTERNAL_FLASH to "ON_EXTERNAL_FLASH",
    )
    private var aeModeValue: Int by Delegates.observable(-1) { _, oldValue, newValue ->
        if (oldValue != newValue) {
            fragmentCameraBinding.run {
                controlAeMode.textCurrent.text = aeModes[newValue]
            }
        }
    }
    private var aeModeControl: Int = 0

    //SENSOR_SENSITIVITY
    private var isoValue: Int by Delegates.observable(0) { _, oldValue, newValue ->
        if (oldValue != newValue) {
            fragmentCameraBinding.run {
                controlSensorSensitivity.textCurrent.text = newValue.toString()
                controlSensorSensitivity.progressCurrent.progress = newValue
            }
        }
    }
    private var isoControl = 0

    //SENSOR_EXPOSURE_TIME
    private var exposureTimeValue: Long by Delegates.observable(0) { _, oldValue, newValue ->
        if (oldValue != newValue) {
            fragmentCameraBinding.run {
                controlSensorExposureTime.textCurrent.text = newValue.toString()
                controlSensorExposureTime.progressCurrent.progress = newValue.toInt() //TODO
            }
        }
    }
    private var exposureTimeControl = 0

    //CONTROL_AE_EXPOSURE_COMPENSATION
    private var exposureCompensationValue: Int by Delegates.observable(-1) { _, oldValue, newValue ->
        if (oldValue != newValue) {
            fragmentCameraBinding.run {
                controlAeExposureCompensation.textCurrent.text = newValue.toString()
                controlAeExposureCompensation.progressCurrent.progress = newValue.toInt()
            }
        }
    }
    private var exposureCompensationControl = 0

    //CONTROL_AF_MODE
    private val afModes = mapOf(
        CameraCharacteristics.CONTROL_AF_MODE_OFF to "OFF",
        CameraCharacteristics.CONTROL_AF_MODE_AUTO to "AUTO",
        CameraCharacteristics.CONTROL_AF_MODE_MACRO to "MACRO",
        CameraCharacteristics.CONTROL_AF_MODE_CONTINUOUS_VIDEO to "CONTINUOUS_VIDEO",
        CameraCharacteristics.CONTROL_AF_MODE_CONTINUOUS_PICTURE to "CONTINUOUS_PICTURE",
        CameraCharacteristics.CONTROL_AF_MODE_EDOF to "EDOF",
    )
    private var afModeValue: Int by Delegates.observable(-1) { _, oldValue, newValue ->
        if (oldValue != newValue) {
            fragmentCameraBinding.run {
                controlAfMode.textCurrent.text = afModes[newValue]
            }
        }
    }
    private var afModeControl: Int = 0

    //LENS_FOCUS_DISTANCE
    private val focusStep = 10_000_000f
    private var focusDistanceValue: Float by Delegates.observable(0f) { _, oldValue, newValue ->
        if (oldValue != newValue) {
            fragmentCameraBinding.run {
                controlLensFocusDistance.textCurrent.text = newValue.toString()
                controlLensFocusDistance.progressCurrent.progress = (newValue*focusStep).toInt()
            }
        }
    }
    private var focusDistanceControl: Float by Delegates.observable(0f) { _, oldValue, newValue ->
        if (oldValue != newValue) {
            fragmentCameraBinding.run {
                controlLensFocusDistance.textControl.text = newValue.toString()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            param1 = it.getString(ARG_PARAM1)
            param2 = it.getString(ARG_PARAM2)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        //TODO remove actionbar
        //fragment binding
        _fragmentCameraBinding = FragmentCameraBinding.inflate(inflater, container, false)
        return fragmentCameraBinding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        fragmentCameraBinding.viewFinder.holder.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceCreated(holder: SurfaceHolder) {
                val previewSize = getPreviewOutputSize(
                    fragmentCameraBinding.viewFinder.display,
                    characteristics,
                    SurfaceHolder::class.java
                )
                Log.d(TAG, "View finder size: ${fragmentCameraBinding.viewFinder.width} x ${fragmentCameraBinding.viewFinder.height}")
                Log.d(TAG, "Selected preview size: $previewSize")

                fragmentCameraBinding.viewFinder.setAspectRatio(
                    previewSize.width,
                    previewSize.height
                )

                view.post { initializeCamera() }
            }

            override fun surfaceChanged(
                holder: SurfaceHolder,
                format: Int,
                width: Int,
                height: Int
            ) = Unit

            override fun surfaceDestroyed(holder: SurfaceHolder) = Unit
        })

        //TODO about viewLifecycleOwner
        relativeOrientation = OrientationLiveData(requireContext(), characteristics).apply {
            observe(viewLifecycleOwner, Observer { orientation ->
                Log.d(TAG, "Orientation changed: $orientation")
            })
        }
    }

    //TODO remove annotation with reduce "min"
    private fun initializeCamera() = lifecycleScope.launch(Dispatchers.Main) {
        camera = openCamera(cameraManager, cameraId, cameraHandler)

        val size = characteristics.get(
            CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)!!
            .getOutputSizes(imageFormat).maxByOrNull { it.height * it.width }!!
        imageReader = ImageReader.newInstance(
            size.width, size.height, imageFormat, IMAGE_BUFFER_SIZE)

        loadControllerValuesByCharacteristics(characteristics)

        val targets = listOf(fragmentCameraBinding.viewFinder.holder.surface, imageReader.surface)

        session = createCaptureSession(camera, targets, cameraHandler)

        val captureRequest = camera.createCaptureRequest(
            CameraDevice.TEMPLATE_PREVIEW
        ).apply {
            addTarget(fragmentCameraBinding.viewFinder.holder.surface)
        }

        val request = captureRequest.build()
        session.capture(request, initialCaptureCallback, cameraHandler)


        fragmentCameraBinding.run {
            setModeController(controlMode, "3A_MODE: ", modes.values.toList(), object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    modeControl = position //TODO arrange
                    changeRequest()
                }
                override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            })

            setModeController(controlAeMode, "AE_MODE: ", aeModes.values.toList(), object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    aeModeControl = position //TODO arrange
                    changeRequest()
                }
                override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            })

            setModeController(controlAfMode, "AF_MODE: ", afModes.values.toList(), object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    afModeControl = position //TODO arrange
                    changeRequest()
                }
                override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            })
        }

        fragmentCameraBinding.captureButton.setOnClickListener {
            it.isEnabled = false

            //TODO about dispatchers
            lifecycleScope.launch(Dispatchers.IO) {
                /*
                takePhoto().use { result ->
                    Log.d(TAG, "Result received: $result")

                    val output = saveResult(result)
                    Log.d(TAG, "Image saved: ${output.absolutePath}")

                    if (output.extension == "jpg") {
                        val exif = ExifInterface(output.absoluteFile)
                        exif.setAttribute(
                            ExifInterface.TAG_ORIENTATION, result.orientation.toString())
                        exif.saveAttributes()
                        Log.d(TAG, "EXIF metadata saved: ${output.absolutePath}")
                    }

                    lifecycleScope.launch(Dispatchers.Main) {
                        //TODO view picture
                    }
                }
                */

                it.post { it.isEnabled = true }
            }
        }

    }

    //TODO integrate
    private fun changeRequest() {
        session.stopRepeating()
        val captureRequestBuilder = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
            addTarget(fragmentCameraBinding.viewFinder.holder.surface)
        }

        if (isValueSet) {
            captureRequestBuilder.set(CaptureRequest.CONTROL_MODE, modeControl)
            captureRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, aeModeControl)
            captureRequestBuilder.set(CaptureRequest.SENSOR_SENSITIVITY, isoControl)
            captureRequestBuilder.set(CaptureRequest.SENSOR_EXPOSURE_TIME, exposureTimeControl.toLong())
            captureRequestBuilder.set(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION, exposureCompensationControl)
            captureRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, afModeControl)
            captureRequestBuilder.set(CaptureRequest.LENS_FOCUS_DISTANCE, focusDistanceControl)
        }

        session.setRepeatingRequest(captureRequestBuilder.build(), captureCallback, cameraHandler)
    }

    private fun loadControllerValuesByCharacteristics(characteristics: CameraCharacteristics) {
        val isoRange = characteristics.get(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE)
        fragmentCameraBinding.run {
            val min = isoRange?.lower ?: 0
            val max = isoRange?.upper ?: 0

            setIntRangeController(controlSensorSensitivity, "ISO", min, max,
                object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                        controlSensorSensitivity.textControl.text = progress.toString()
                    }
                    override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
                    override fun onStopTrackingTouch(seekBar: SeekBar?) {
                        isoControl = seekBar?.progress ?: 0
                        changeRequest()
                    }
                }
            )
        }

        val expoureTimeRange = characteristics.get(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE)
        fragmentCameraBinding.run {
            val min = expoureTimeRange?.lower ?: 0
            val max = expoureTimeRange?.upper ?: 0

            setIntRangeController(controlSensorExposureTime, "Exposure Time", min.toInt(), max.toInt(),
                object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                        controlSensorExposureTime.textControl.text = progress.toString()
                    }
                    override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
                    override fun onStopTrackingTouch(seekBar: SeekBar?) {
                        exposureTimeControl = seekBar?.progress ?: 0
                        changeRequest()
                    }
                }
            )
        }

        val exposureCompensationRange = characteristics.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_RANGE)
        fragmentCameraBinding.run {
            val min = exposureCompensationRange?.lower ?: 0
            val max = exposureCompensationRange?.upper ?: 0

            setIntRangeController(controlAeExposureCompensation, "Exposure Compensation", min.toInt(), max.toInt(),
                object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                        controlAeExposureCompensation.textControl.text = progress.toString()
                    }
                    override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
                    override fun onStopTrackingTouch(seekBar: SeekBar?) {
                        exposureCompensationControl = seekBar?.progress ?: 0
                        changeRequest()
                    }
                }
            )
        }

        val minimumFocusDistance = characteristics.get(CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE)?: 0.0f
        fragmentCameraBinding.run {
            //TODO arrange number
            val min = 0
            val max = (minimumFocusDistance * focusStep).toInt()

            setFloatRangeController(controlLensFocusDistance, "Lens Focus Distance", min, max, focusStep,
                object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                        controlLensFocusDistance.textControl.text = (progress / focusStep).toString()
                    }
                    override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
                    override fun onStopTrackingTouch(seekBar: SeekBar?) {
                        val progressValue = seekBar?.progress ?: 0
                        focusDistanceControl = progressValue / focusStep
                        changeRequest()
                    }
                }
            )
        }
    }

    private fun setModeController(
        layout: LayoutModeControlBinding,
        title: String,
        data: List<String>,
        itemSelectedListener: AdapterView.OnItemSelectedListener
    ) {
        layout.textTitle.text = title
        layout.spinnerMode.adapter =
            ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, data)
        layout.spinnerMode.onItemSelectedListener = itemSelectedListener
    }

    private fun setIntRangeController(
        layout: LayoutRangeControlBinding,
        title: String,
        min: Int,
        max: Int,
        seekbarListener: SeekBar.OnSeekBarChangeListener) {
        layout.run{
            textTitle.text = title
            textMin.text = min.toString()
            textMax.text = max.toString()
            progressCurrent.min = min
            progressCurrent.max = max
            seekbarControl.min = min
            seekbarControl.max = max

            seekbarControl.setOnSeekBarChangeListener(seekbarListener)
        }
    }

    private fun setFloatRangeController(
        layout: LayoutRangeControlBinding,
        title: String,
        min: Int,
        max: Int,
        div: Float,
        seekbarListener: SeekBar.OnSeekBarChangeListener) {
        layout.run{
            textTitle.text = title
            textMin.text = (min/div).toString()
            textMax.text = (max/div).toString()
            progressCurrent.min = min
            progressCurrent.max = max
            seekbarControl.min = min
            seekbarControl.max = max

            seekbarControl.setOnSeekBarChangeListener(seekbarListener)
        }
    }

    private fun handleInitialCaptureRequest(captureResult: TotalCaptureResult) {
        val timestamp = captureResult.get(CaptureResult.SENSOR_TIMESTAMP)

        if (_fragmentCameraBinding == null) return

        CoroutineScope(Dispatchers.Main).launch {
            //ISO
            //TODO separate saveValues()
            modeValue = captureResult.get(CaptureResult.CONTROL_MODE)?: -1
            fragmentCameraBinding.controlMode.spinnerMode.setSelection(modeValue)

            aeModeValue = captureResult.get(CaptureResult.CONTROL_AE_MODE)?: -1
            fragmentCameraBinding.controlAeMode.spinnerMode.setSelection(aeModeValue)

            isoValue = captureResult.get(CaptureResult.SENSOR_SENSITIVITY)?: -1
            isoControl = isoValue
            fragmentCameraBinding.controlSensorSensitivity.seekbarControl.progress = isoControl

            exposureTimeValue = captureResult.get(CaptureResult.SENSOR_EXPOSURE_TIME)?: -1L
            exposureTimeControl = exposureTimeValue.toInt()
            fragmentCameraBinding.controlSensorExposureTime.seekbarControl.progress = exposureTimeControl

            exposureCompensationValue = captureResult.get(CaptureResult.CONTROL_AE_EXPOSURE_COMPENSATION)?: -1
            exposureCompensationControl = exposureCompensationValue
            fragmentCameraBinding.controlAeExposureCompensation.seekbarControl.progress = exposureCompensationControl

            //TODO ***** af states
            afModeValue = captureResult.get(CaptureResult.CONTROL_AF_MODE)?: -1
            fragmentCameraBinding.controlAfMode.spinnerMode.setSelection(afModeValue)

            focusDistanceValue = captureResult.get(CaptureResult.LENS_FOCUS_DISTANCE)?: 0.0f
            focusDistanceControl = focusDistanceValue
            fragmentCameraBinding.controlLensFocusDistance.seekbarControl.progress = (focusDistanceControl * focusStep).toInt()

            isValueSet = true

            Log.d(TAG, "(test) handleInitialCaptureRequest")
        }
        changeRequest()
    }

    private fun handleCaptureResult(captureResult: TotalCaptureResult) {
        val timestamp = captureResult.get(CaptureResult.SENSOR_TIMESTAMP)

        if (_fragmentCameraBinding == null) return

        CoroutineScope(Dispatchers.Main).launch {
            modeValue = captureResult.get(CaptureResult.CONTROL_MODE)?: -1
            aeModeValue = captureResult.get(CaptureResult.CONTROL_AE_MODE)?: -1
            isoValue = captureResult.get(CaptureResult.SENSOR_SENSITIVITY)?: -1
            exposureTimeValue = captureResult.get(CaptureResult.SENSOR_EXPOSURE_TIME)?: -1L
            exposureCompensationValue = captureResult.get(CaptureResult.CONTROL_AE_EXPOSURE_COMPENSATION)?: -1
            afModeValue = captureResult.get(CaptureResult.CONTROL_AF_MODE)?: -1
            focusDistanceValue = captureResult.get(CaptureResult.LENS_FOCUS_DISTANCE)?: 0.0f
        }

    }

    //TODO permission
    @SuppressLint("MissingPermission")
    private suspend fun openCamera(
        manager: CameraManager,
        cameraId: String,
        handler: Handler? = null) : CameraDevice = suspendCancellableCoroutine { cont ->
        manager.openCamera(cameraId, object : CameraDevice.StateCallback() {
            override fun onOpened(camera: CameraDevice) = cont.resume(camera)

            override fun onDisconnected(camera: CameraDevice) {
                Log.w(TAG, "Camera $cameraId has been disconnected")
                requireActivity().finish()
            }

            override fun onError(camera: CameraDevice, error: Int) {
                val msg = when (error) {
                    ERROR_CAMERA_DEVICE -> "Fatal (device)"
                    ERROR_CAMERA_DISABLED -> "Device policy"
                    ERROR_CAMERA_IN_USE -> "Camera in use"
                    ERROR_CAMERA_SERVICE -> "Fatal (service)"
                    ERROR_MAX_CAMERAS_IN_USE -> "Maximum cameras in use"
                    else -> "Unknown"
                }
                val exc = RuntimeException("Camera $cameraId error: ($error) $msg")
                Log.e(TAG, exc.message, exc)
                if (cont.isActive) cont.resumeWithException(exc)
            }
        }, handler)
    }

    private suspend fun createCaptureSession(
        device: CameraDevice,
        targets: List<Surface>,
        handler: Handler? = null
    ): CameraCaptureSession = suspendCoroutine { cont ->
        //TODO deprecated
        device.createCaptureSession(targets, object : CameraCaptureSession.StateCallback() {
            override fun onConfigured(session: CameraCaptureSession) = cont.resume(session)

            override fun onConfigureFailed(session: CameraCaptureSession) {
                val exc = RuntimeException("Camera ${device.id} session configuration failed")
                Log.e(TAG, exc.message, exc)
                cont.resumeWithException(exc)
            }
        }, handler)
    }

    private suspend fun takePhoto(): CombinedCaptureResult = suspendCoroutine { cont ->
        while (imageReader.acquireNextImage() != null) {
        }

        val imageQueue = ArrayBlockingQueue<Image>(IMAGE_BUFFER_SIZE)
        imageReader.setOnImageAvailableListener({ reader ->
            val image = reader.acquireNextImage()
            Log.d(TAG, "Image available in queue: ${image.timestamp}")
            imageQueue.add(image)
        }, imageReaderHandler)

        //TODO what is TEMPLATE_STILL_CAPTURE?
        val captureResult = session.device.createCaptureRequest(
            CameraDevice.TEMPLATE_STILL_CAPTURE).apply { addTarget(imageReader.surface) }
        session.capture(captureResult.build(), object : CameraCaptureSession.CaptureCallback() {
            override fun onCaptureStarted(
                session: CameraCaptureSession,
                request: CaptureRequest,
                timestamp: Long,
                frameNumber: Long
            ) {
                super.onCaptureStarted(session, request, timestamp, frameNumber)
                //TODO capture start action
            }

            override fun onCaptureCompleted(
                session: CameraCaptureSession,
                request: CaptureRequest,
                result: TotalCaptureResult
            ) {
                super.onCaptureCompleted(session, request, result)
                val resultTimestamp = result.get(CaptureResult.SENSOR_TIMESTAMP)
                Log.d(TAG, "Capture result received: $resultTimestamp")

                val exc = TimeoutException("Image dequeuing took too long")
                val timeoutRunnable = Runnable { cont.resumeWithException(exc) }
                imageReaderHandler.postDelayed(timeoutRunnable, IMAGE_CAPTURE_TIMEOUT_MILLIS)

                lifecycleScope.launch(cont.context) {
                    while (true) {
                        val image = imageQueue.take()
                        //TODO what mean?
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                                image.format != ImageFormat.DEPTH_JPEG &&
                                image.timestamp != resultTimestamp) continue
                        Log.d(TAG, "Matching image dequeued: ${image.timestamp}")

                        imageReaderHandler.removeCallbacks(timeoutRunnable)
                        imageReader.setOnImageAvailableListener(null, null)

                        while (imageQueue.size > 0) {
                            imageQueue.take().close()
                        }

                        val rotation = relativeOrientation.value ?: 0
                        val mirrored = characteristics.get(CameraCharacteristics.LENS_FACING) ==
                                CameraCharacteristics.LENS_FACING_FRONT
                        //TODO about orientation
                        val exifOrientation = computeExifOrientation(rotation, mirrored)

                        cont.resume(CombinedCaptureResult(
                            image, result, exifOrientation, imageReader.imageFormat))
                    }
                }
            }
        }, cameraHandler)
    }

    private suspend fun saveResult(result: CombinedCaptureResult) : File = suspendCoroutine { cont ->
        when (result.format) {
            ImageFormat.JPEG, ImageFormat.DEPTH_JPEG -> {
                val buffer = result.image.planes[0].buffer
                val bytes = ByteArray(buffer.remaining()).apply { buffer.get(this) }
                try {
                    val output = createFile(requireContext(), "jpg")
                    FileOutputStream(output).use { it.write(bytes) }
                } catch (exc: IOException) {
                    Log.e(TAG, "Unable to write JPEG image to file", exc)
                    cont.resumeWithException(exc)
                }
            }
            //TODO YUV image

            //TODO ImageFormat.RAW_SENSOR
            else -> {
            }
        }
    }

    //TODO onresume

    override fun onStop() {
        super.onStop()
        try {
            camera.close()
        } catch (exc: Throwable) {
            Log.e(TAG, "Error closing camera", exc)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraThread.quitSafely()
        imageReaderThread.quitSafely()
    }

    override fun onDestroyView() {
        _fragmentCameraBinding = null
        super.onDestroyView()
    }

    companion object {
        private const val TAG = "CameraFragment"

        private const val IMAGE_BUFFER_SIZE: Int = 3

        private const val IMAGE_CAPTURE_TIMEOUT_MILLIS: Long = 5000

        data class CombinedCaptureResult(
            val image: Image,
            val metadata: CaptureResult,
            val orientation: Int,
            val format: Int
        ) : Closeable {
            override fun close() = image.close()
        }

        private fun createFile(context: Context, extension: String): File {
            val sdf = SimpleDateFormat("yyyy_MM_dd_HH_mm_ss_sss", Locale.KOREA)
            return File(context.filesDir, "IMG_${sdf.format(Date())}.$extension")
        }

        /**
         * Use this factory method to create a new instance of
         * this fragment using the provided parameters.
         *
         * @param param1 Parameter 1.
         * @param param2 Parameter 2.
         * @return A new instance of fragment CameraFragment.
         */
        // TODO: Rename and change types and number of parameters
        @JvmStatic
        fun newInstance(param1: String, param2: String) =
            CameraFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_PARAM1, param1)
                    putString(ARG_PARAM2, param2)
                }
            }


    }
}
