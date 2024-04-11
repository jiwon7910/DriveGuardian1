package com.example.poseexercise.views.activity

import android.Manifest
import android.app.Fragment
import android.content.pm.PackageManager
import android.content.res.AssetManager
import android.hardware.Camera
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.Image.Plane
import android.media.ImageReader
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.Trace
import android.util.Size
import android.view.Surface
import android.view.View
import android.view.ViewTreeObserver
import android.view.WindowManager
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import com.example.poseexercise.R
import com.example.poseexercise.env.ImageUtils
import com.example.poseexercise.env.Logger
import com.example.poseexercise.views.fragment.CameraConnectionFragment
import com.example.poseexercise.views.fragment.LegacyCameraConnectionFragment
import com.google.android.material.bottomsheet.BottomSheetBehavior
import java.io.IOException


abstract class CameraActivity : AppCompatActivity(), ImageReader.OnImageAvailableListener,
    Camera.PreviewCallback, View.OnClickListener {
    protected var previewWidth = 0
    protected var previewHeight = 0
    val isDebug = false
    protected var handler: Handler? = null
    private var handlerThread: HandlerThread? = null
    private var useCamera2API = false
    private var isProcessingFrame = false
    private val yuvBytes = arrayOfNulls<ByteArray>(3)
    private var rgbBytes: IntArray? = null
    protected var luminanceStride = 0
        private set
    protected var defaultModelIndex = 0
    protected var defaultDeviceIndex = 0
    private var postInferenceCallback: Runnable? = null
    private var imageConverter: Runnable? = null
    protected var modelStrings = ArrayList<String>()
    private var bottomSheetLayout: LinearLayout? = null
    private var gestureLayout: LinearLayout? = null
    private var sheetBehavior: BottomSheetBehavior<LinearLayout?>? = null
    protected var frameValueTextView: TextView? = null
    protected var cropValueTextView: TextView? = null
    protected var inferenceTimeTextView: TextView? = null
    protected var bottomSheetArrowImageView: ImageView? = null
    private var plusImageView: ImageView? = null
    private var minusImageView: ImageView? = null
    protected var deviceView: ListView? = null
    protected var threadsTextView: TextView? = null
    protected val modelView: ListView? = null

    /** Current indices of device and model.  */
    var currentDevice = -1
    var currentModel = -1
    var currentNumThreads = -1
    var deviceStrings = ArrayList<String>()

    public fun CameraActivity(){

    }

    protected fun getRgbBytes(): IntArray? {
            imageConverter!!.run()
            return rgbBytes
    }



    override fun onCreate(savedInstanceState: Bundle?) {
        LOGGER.d("onCreate $this")
        super.onCreate(null)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(R.layout.tfe_od_activity_camera)
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar!!.setDisplayShowTitleEnabled(false)
        if (hasPermission()) {
            setFragment()
        } else {
            requestPermission()
        }
        val threadsTextView = findViewById<TextView>(R.id.threads)
        currentNumThreads = threadsTextView.getText().toString().trim { it <= ' ' }.toInt()
        val plusImageView = findViewById<ImageView>(R.id.plus)
        val minusImageView = findViewById<ImageView>(R.id.minus)
        val deviceView = findViewById<ListView>(R.id.device_list)
        deviceStrings.add("CPU")
        deviceStrings.add("GPU")
        deviceStrings.add("NNAPI")
        deviceView.setChoiceMode(ListView.CHOICE_MODE_SINGLE)
        val deviceAdapter = ArrayAdapter<String>(
            this@CameraActivity, R.layout.deviceview_row, R.id.deviceview_row_text, deviceStrings
        )
        deviceView.setAdapter(deviceAdapter)
        deviceView.setItemChecked(defaultDeviceIndex, true)
        currentDevice = defaultDeviceIndex
        deviceView.setOnItemClickListener { parent, view, position, id -> updateActiveModel() }
        val bottomSheetLayout = findViewById<LinearLayout>(R.id.bottom_sheet_layout)
        val gestureLayout = findViewById<LinearLayout>(R.id.gesture_layout)
        sheetBehavior = BottomSheetBehavior.from(bottomSheetLayout)
        val bottomSheetArrowImageView = findViewById<ImageView>(R.id.bottom_sheet_arrow)
        val modelView = findViewById<ListView>(R.id.model_list)
        modelStrings = getModelStrings(assets, ASSET_PATH)
        modelView.setChoiceMode(ListView.CHOICE_MODE_SINGLE)
        val modelAdapter = ArrayAdapter<String>(
            this@CameraActivity, R.layout.listview_row, R.id.listview_row_text, modelStrings
        )
        modelView.setAdapter(modelAdapter)
        modelView.setItemChecked(defaultModelIndex, true)
        currentModel = defaultModelIndex
        modelView.setOnItemClickListener { parent, view, position, id -> updateActiveModel() }
        val vto = gestureLayout.getViewTreeObserver()
        vto.addOnGlobalLayoutListener(
            object : ViewTreeObserver.OnGlobalLayoutListener {
                override fun onGlobalLayout() {
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN) {
                        gestureLayout.getViewTreeObserver().removeGlobalOnLayoutListener(this)
                    } else {
                        gestureLayout.getViewTreeObserver().removeOnGlobalLayoutListener(this)
                    }
                    //                int width = bottomSheetLayout.getMeasuredWidth();
                    val height = gestureLayout.getMeasuredHeight()
                    sheetBehavior!!.peekHeight = height
                }
            })
        sheetBehavior!!.isHideable = false
        sheetBehavior!!.setBottomSheetCallback(
            object : BottomSheetBehavior.BottomSheetCallback() {
                override fun onStateChanged(bottomSheet: View, newState: Int) {
                    when (newState) {
                        BottomSheetBehavior.STATE_HIDDEN -> {}
                        BottomSheetBehavior.STATE_EXPANDED -> {
                            bottomSheetArrowImageView.setImageResource(R.drawable.icn_chevron_down)
                        }

                        BottomSheetBehavior.STATE_COLLAPSED -> {
                            bottomSheetArrowImageView.setImageResource(R.drawable.icn_chevron_up)
                        }

                        BottomSheetBehavior.STATE_DRAGGING -> {}
                        BottomSheetBehavior.STATE_SETTLING -> bottomSheetArrowImageView.setImageResource(
                            R.drawable.icn_chevron_up
                        )
                    }
                }

                override fun onSlide(bottomSheet: View, slideOffset: Float) {}
            })
        frameValueTextView = findViewById<TextView>(R.id.frame_info)
        cropValueTextView = findViewById<TextView>(R.id.crop_info)
        inferenceTimeTextView = findViewById<TextView>(R.id.inference_info)
        plusImageView.setOnClickListener(this)
        minusImageView.setOnClickListener(this)
    }

    protected fun getModelStrings(mgr: AssetManager, path: String?): ArrayList<String> {
        val res = ArrayList<String>()
        try {
            val files = mgr.list(path!!)
            for (file in files!!) {
                val splits = file.split("\\.".toRegex()).dropLastWhile { it.isEmpty() }
                    .toTypedArray()
                if (splits[splits.size - 1] == "tflite") {
                    res.add(file)
                }
            }
        } catch (e: IOException) {
            System.err.println("getModelStrings: " + e.message)
        }
        return res
    }



    protected val luminance: ByteArray?
        protected get() = yuvBytes[0]

    /** Callback for android.hardware.Camera API  */
    override fun onPreviewFrame(bytes: ByteArray, camera: Camera) {
        if (isProcessingFrame) {
            LOGGER.w("Dropping frame!")
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
            LOGGER.e(e, "Exception!")
            return
        }
        isProcessingFrame = true
        yuvBytes[0] = bytes
        luminanceStride = previewWidth
        imageConverter = Runnable {
            ImageUtils.convertYUV420SPToARGB8888(
                bytes,
                previewWidth,
                previewHeight,
                rgbBytes
            )
        }
        postInferenceCallback = Runnable {
            camera.addCallbackBuffer(bytes)
            isProcessingFrame = false
        }
        processImage()
    }



    /** Callback for Camera2 API  */
    override fun onImageAvailable(reader: ImageReader) {
        // We need wait until we have some size from onPreviewSizeChosen
        if (previewWidth == 0 || previewHeight == 0) {
            return
        }
        if (rgbBytes == null) {
            rgbBytes = IntArray(previewWidth * previewHeight)
        }
        try {
            val image = reader.acquireLatestImage() ?: return
            if (isProcessingFrame) {
                image.close()
                return
            }
            isProcessingFrame = true
            Trace.beginSection("imageAvailable")
            val planes = image.planes
            fillBytes(planes, yuvBytes)
            luminanceStride = planes[0].rowStride
            val uvRowStride = planes[1].rowStride
            val uvPixelStride = planes[1].pixelStride
            imageConverter = object : Runnable {
                override fun run() {
                    ImageUtils.convertYUV420ToARGB8888(
                        yuvBytes[0],
                        yuvBytes[1],
                        yuvBytes[2],
                        previewWidth,
                        previewHeight,
                        luminanceStride,
                        uvRowStride,
                        uvPixelStride,
                        rgbBytes
                    )
                }
            }
            postInferenceCallback = Runnable {
                image.close()
                isProcessingFrame = false
            }
            processImage()
        } catch (e: Exception) {
            LOGGER.e(e, "Exception!")
            Trace.endSection()
            return
        }
        Trace.endSection()
    }

    @Synchronized
    public override fun onStart() {
        LOGGER.d("onStart $this")
        super.onStart()
    }

    @Synchronized
    public override fun onResume() {
        LOGGER.d("onResume $this")
        super.onResume()
        handlerThread = HandlerThread("inference")
        handlerThread!!.start()
        handler = Handler(handlerThread!!.looper)
    }

    @Synchronized
    public override fun onPause() {
        LOGGER.d("onPause $this")
        handlerThread!!.quitSafely()
        try {
            handlerThread!!.join()
            handlerThread = null
            handler = null
        } catch (e: InterruptedException) {
            LOGGER.e(e, "Exception!")
        }
        super.onPause()
    }

    @Synchronized
    public override fun onStop() {
        LOGGER.d("onStop $this")
        super.onStop()
    }

    @Synchronized
    public override fun onDestroy() {
        LOGGER.d("onDestroy $this")
        super.onDestroy()
    }

    @Synchronized
    protected fun runInBackground(r: Runnable?) {
        if (handler != null) {
            handler!!.post(r!!)
        }
    }



    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSIONS_REQUEST) {
            if (allPermissionsGranted(grantResults)) {
                setFragment()
            } else {
                requestPermission()
            }
        }
    }

    private fun hasPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            checkSelfPermission(PERMISSION_CAMERA) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    private fun requestPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (shouldShowRequestPermissionRationale(PERMISSION_CAMERA)) {
                Toast.makeText(
                    this@CameraActivity,
                    "Camera permission is required for this demo",
                    Toast.LENGTH_LONG
                )
                    .show()
            }
            requestPermissions(arrayOf(PERMISSION_CAMERA), PERMISSIONS_REQUEST)
        }
    }

    // Returns true if the device supports the required hardware level, or better.
    private fun isHardwareLevelSupported(
        characteristics: CameraCharacteristics, requiredLevel: Int
    ): Boolean {
        val deviceLevel = characteristics.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL)!!
        return if (deviceLevel == CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY) {
            requiredLevel == deviceLevel
        } else requiredLevel <= deviceLevel
        // deviceLevel is not LEGACY, can use numerical sort
    }

    private fun chooseCamera(): String? {
        val manager = getSystemService(CAMERA_SERVICE) as CameraManager
        try {
            for (cameraId in manager.cameraIdList) {
                val characteristics = manager.getCameraCharacteristics(cameraId)

                // We don't use a front facing camera in this sample.
                val facing = characteristics.get(CameraCharacteristics.LENS_FACING)
                if (facing != null && facing == CameraCharacteristics.LENS_FACING_BACK) {
                    continue
                }
                val map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                    ?: continue

                // Fallback to camera1 API for internal cameras that don't have full support.
                // This should help with legacy situations where using the camera2 API causes
                // distorted or otherwise broken previews.
                useCamera2API = (facing == CameraCharacteristics.LENS_FACING_FRONT
                        || isHardwareLevelSupported(
                    characteristics, CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_FULL
                ))
                LOGGER.i("Camera API lv2?: %s", useCamera2API)
                return cameraId
            }
        } catch (e: CameraAccessException) {
            LOGGER.e(e, "Not allowed to access camera")
        }
        return null
    }


    protected fun setFragment() {
        val cameraId = chooseCamera()
        val fragment: Fragment
        if (useCamera2API) {
            val camera2Fragment: CameraConnectionFragment = CameraConnectionFragment.newInstance(
                { size, rotation ->
                    previewHeight = size.height
                    previewWidth = size.width
                    this@CameraActivity.onPreviewSizeChosen(size, rotation)
                },
                this,
                getLayoutId(),
                getDesiredPreviewFrameSize()
            )
            camera2Fragment.setCamera(cameraId)
            fragment = camera2Fragment
        } else {
            fragment =
                LegacyCameraConnectionFragment(
                    this,
                    getLayoutId(),
                    getDesiredPreviewFrameSize()
                )
        }
        fragmentManager.beginTransaction().replace(R.id.container, fragment).commit()
    }


    protected fun fillBytes(planes: Array<Plane>, yuvBytes: Array<ByteArray?>) {
        // Because of the variable row stride it's not possible to know in
        // advance the actual necessary dimensions of the yuv planes.
        for (i in planes.indices) {
            val buffer = planes[i].buffer
            if (yuvBytes[i] == null) {
                LOGGER.d("Initializing buffer %d at size %d", i, buffer.capacity())
                yuvBytes[i] = ByteArray(buffer.capacity())
            }
            buffer[yuvBytes[i]]
        }
    }

    protected fun readyForNextImage() {
        if (postInferenceCallback != null) {
            postInferenceCallback!!.run()
        }
    }

    protected val screenOrientation: Int
        protected get() = when (windowManager.defaultDisplay.rotation) {
            Surface.ROTATION_270 -> 270
            Surface.ROTATION_180 -> 180
            Surface.ROTATION_90 -> 90
            else -> 0
        }

    //  @Override
    //  public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
    //    setUseNNAPI(isChecked);
    //    if (isChecked) apiSwitchCompat.setText("NNAPI");
    //    else apiSwitchCompat.setText("TFLITE");
    //  }

    override fun onClick(v: View) {
        if (v.id == R.id.plus) {
            val threads = threadsTextView!!.text.toString().trim { it <= ' ' }
            var numThreads = threads.toInt()
            if (numThreads >= 9) return
            numThreads++
            threadsTextView!!.text = numThreads.toString()
            setNumThreads(numThreads)
        } else if (v.id == R.id.minus) {
            val threads = threadsTextView!!.text.toString().trim { it <= ' ' }
            var numThreads = threads.toInt()
            if (numThreads == 1) {
                return
            }
            numThreads--
            threadsTextView!!.text = numThreads.toString()
            setNumThreads(numThreads)
        }
    }

    protected fun showFrameInfo(frameInfo: String?) {
        frameValueTextView!!.text = frameInfo
    }

    protected fun showCropInfo(cropInfo: String?) {
        cropValueTextView!!.text = cropInfo
    }

    protected fun showInference(inferenceTime: String?) {
        inferenceTimeTextView!!.text = inferenceTime
    }

    protected abstract fun updateActiveModel()
    protected abstract fun processImage()
    protected abstract fun onPreviewSizeChosen(size: Size?, rotation: Int)
    protected abstract fun getLayoutId(): Int

    protected abstract fun getDesiredPreviewFrameSize(): Size?

    protected abstract fun setNumThreads(numThreads: Int)
    protected abstract fun setUseNNAPI(isChecked: Boolean)

    companion object {
        private val LOGGER = Logger()
        private const val PERMISSIONS_REQUEST = 1
        private const val PERMISSION_CAMERA = Manifest.permission.CAMERA
        private const val ASSET_PATH = ""
        private fun allPermissionsGranted(grantResults: IntArray): Boolean {
            for (result in grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    return false
                }
            }
            return true
        }
    }
}