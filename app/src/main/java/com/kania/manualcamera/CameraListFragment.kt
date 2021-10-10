package com.kania.manualcamera

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.ImageFormat
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Bundle
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.RadioButton
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.navigation.Navigation
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.lang.StringBuilder

private const val PERMISSION_REQUEST_CODE = 100
private val PERMISSION_REQUIRED = arrayOf(Manifest.permission.CAMERA)

/**
 * A simple [Fragment] subclass.
 * Use the [CameraListFragment.newInstance] factory method to
 * create an instance of this fragment.
 */
class CameraListFragment : Fragment() {

    companion object {
        const val TAG = "CameraLog"
        const val DEFAULT_CAMERA_ID = "0"

        fun hasPermission(context: Context) = PERMISSION_REQUIRED.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }

        /**
         * Use this factory method to create a new instance of
         * this fragment using the provided parameters.
         *
         * @return A new instance of fragment CameraListFragment.
         */
        @JvmStatic
        fun newInstance() = CameraListFragment()
    }

    private data class CameraData(val cameraId: String, val title: String, val info: String)

    private lateinit var mCharacteristics: CameraCharacteristics
    private var mSelectedCameraId: String = DEFAULT_CAMERA_ID


    //TODO use viewbinding on fragment

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!hasPermission(requireContext())) {
            requestPermissions(PERMISSION_REQUIRED, PERMISSION_REQUEST_CODE)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_camera_list, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.findViewById<RecyclerView>(R.id.listCameras).run {
            layoutManager = LinearLayoutManager(requireContext())

            val cameraDataList = getCameraList()

            val layoutId = R.layout.item_camera_info
            adapter = GenericListAdapter(cameraDataList, itemLayoutId = layoutId, null) { view, item, position ->
                view.findViewById<TextView>(R.id.textTitle).text = item.title
                view.findViewById<TextView>(R.id.textInfo).text = item.info
                view.setOnClickListener{
                    //TODO start
                }
            }
            this.addItemDecoration(DividerItemDecoration(requireContext(), DividerItemDecoration.VERTICAL))
        }

        (view.findViewById(R.id.btnStartCamera) as Button).setOnClickListener {
            Navigation.findNavController(view).navigate(R.id.action_cameraListFragment_to_cameraFragment)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(context, "Permission request denied", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun getLensTypeString(value: Int) = when(value) {
        CameraCharacteristics.LENS_FACING_BACK -> "Back"
        CameraCharacteristics.LENS_FACING_FRONT -> "Front"
        CameraCharacteristics.LENS_FACING_EXTERNAL -> "External"
        else -> "Unknown Lens Facing"
    }

    private fun getHardwareLevelString(value: Int) = when(value) {
        CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY -> "LEGACY"
        CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_EXTERNAL -> "EXTERNAL"
        CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LIMITED -> "LIMITED"
        CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_FULL -> "FULL"
        CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_3 -> "LEVEL_3"
        else -> "Unknown Hardware Level"
    }

    private fun getCameraInfoString(cameraId: String, characteristics: CameraCharacteristics): String {
        val sizeList = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)?.getOutputSizes(ImageFormat.YUV_420_888)
        if (sizeList != null) {
            Log.d(TAG, sizeList.joinToString(","))
        }
        val maxSize = sizeList?.maxByOrNull { it.width * it.height }
        val lensType = getLensTypeString(characteristics.get(CameraCharacteristics.LENS_FACING)?: -1)
        val hardwareLevel = getHardwareLevelString(characteristics.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL)?: -1)

        return "ID($cameraId) $lensType ${maxSize?.width}x${maxSize?.height} $hardwareLevel"
    }

    private fun <T> getInfoStringByKey(key: CameraCharacteristics.Key<T>) =
        (mCharacteristics.get(key)).toString()

    private fun getCameraList(): List<CameraData> {
        val cameraDataList = mutableListOf<CameraData>()
        val cameraManager =
            requireContext().getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val cameraIdList = cameraManager.cameraIdList
        for (cameraId in cameraIdList) {
            mCharacteristics = cameraManager.getCameraCharacteristics(cameraId)
            val cameraInfoStr = getCameraInfoString(cameraId, mCharacteristics)

            val sb = StringBuilder().apply {
                append("SENSOR_INFO_SENSITIVITY_RANGE : " + getInfoStringByKey(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE))
                append("\nSENSOR_MAX_ANALOG_SENSITIVITY : " + getInfoStringByKey(CameraCharacteristics.SENSOR_MAX_ANALOG_SENSITIVITY))
                append("\nSENSOR_INFO_EXPOSURE_TIME_RANGE : " + getInfoStringByKey(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE))
                append("\nCONTROL_AE_COMPENSATION_RANGE : " + getInfoStringByKey(CameraCharacteristics.CONTROL_AE_COMPENSATION_RANGE))
                append("\nCONTROL_AE_COMPENSATION_STEP : " + getInfoStringByKey(CameraCharacteristics.CONTROL_AE_COMPENSATION_STEP))
                append("\nLENS_INFO_FOCUS_DISTANCE_CALIBRATION : " + getInfoStringByKey(CameraCharacteristics.LENS_INFO_FOCUS_DISTANCE_CALIBRATION))
                append("\nLENS_INFO_MINIMUM_FOCUS_DISTANCE : " + getInfoStringByKey(CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE))
            }

            cameraDataList.add(
                CameraData(cameraId, cameraInfoStr, sb.toString())
            )
        }
        return cameraDataList
    }
}
