package com.example.poseexercise.views.activity

import android.graphics.Bitmap
import android.graphics.Bitmap.Config
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Paint.Style
import android.graphics.RectF
import android.graphics.Typeface
import android.media.ImageReader
import android.media.ImageReader.OnImageAvailableListener
import android.os.SystemClock
import android.util.Log
import android.util.Size
import android.util.TypedValue
import android.view.View
import android.widget.Toast
import com.example.poseexercise.R
import java.io.IOException
import java.util.LinkedList
import com.example.poseexercise.customview.OverlayView
import com.example.poseexercise.customview.OverlayView.DrawCallback
import com.example.poseexercise.env.BorderedText
import com.example.poseexercise.env.ImageUtils
import com.example.poseexercise.env.Logger
import com.example.poseexercise.tflite.Classifier
import com.example.poseexercise.tflite.DetectorFactory
import com.example.poseexercise.tflite.YoloV5Classifier
import com.example.poseexercise.tracking.MultiBoxTracker


public class DetectorActivity : CameraActivity(), ImageReader.OnImageAvailableListener {
    public fun DetectorActivity(){

    }
    var trackingOverlay: OverlayView? = null
    private var sensorOrientation: Int? = null
    private var detector: YoloV5Classifier? = null
    private var lastProcessingTimeMs: Long = 0
    private var rgbFrameBitmap: Bitmap? = null
    private var croppedBitmap: Bitmap? = null
    private var cropCopyBitmap: Bitmap? = null
    private var computingDetection = false
    private var timestamp: Long = 0
    private var frameToCropTransform: Matrix? = null
    private var cropToFrameTransform: Matrix? = null
    private var tracker: MultiBoxTracker? = null
    private var borderedText: BorderedText? = null
    private var modelIndex: Int? = null
    public override fun onPreviewSizeChosen(size: Size?, rotation: Int) {
        val textSizePx = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, TEXT_SIZE_DIP, resources.displayMetrics
        )
        borderedText = BorderedText(textSizePx)
        borderedText!!.setTypeface(Typeface.MONOSPACE)
        tracker = MultiBoxTracker(this)
        modelIndex = modelView?.checkedItemPosition
        val modelString = "best-fp16_240103.tflite"
        try {
            detector = DetectorFactory.getDetector(assets, modelString)
        } catch (e: IOException) {
            e.printStackTrace()
            LOGGER.e(e, "Exception initializing classifier!")
            val toast = Toast.makeText(
                applicationContext, "Classifier could not be initialized", Toast.LENGTH_SHORT
            )
            toast.show()
            finish()
        }
        val cropSize = detector!!.inputSize
        previewWidth = size!!.width
        previewHeight = size.height
        sensorOrientation = rotation - screenOrientation
        LOGGER.i("Camera orientation relative to screen canvas: %d", sensorOrientation)
        LOGGER.i("Initializing at size %dx%d", previewWidth, previewHeight)
        rgbFrameBitmap =
            Bitmap.createBitmap(previewWidth, previewHeight, Bitmap.Config.ARGB_8888)
        croppedBitmap = Bitmap.createBitmap(cropSize, cropSize, Bitmap.Config.ARGB_8888)
        frameToCropTransform = ImageUtils.getTransformationMatrix(
            previewWidth, previewHeight,
            cropSize, cropSize,
            sensorOrientation!!, MAINTAIN_ASPECT
        )
        cropToFrameTransform = Matrix()
        frameToCropTransform?.invert(cropToFrameTransform)
        trackingOverlay = findViewById<View>(R.id.tracking_overlay) as OverlayView
        trackingOverlay!!.addCallback { canvas ->
            tracker!!.draw(canvas)
            if (isDebug) {
                tracker!!.drawDebug(canvas)
            }
        }
        tracker!!.setFrameConfiguration(previewWidth, previewHeight, sensorOrientation!!)
    }

    override fun updateActiveModel() {
        // Get UI information before delegating to background
        modelIndex = modelView!!.checkedItemPosition
        val deviceIndex = deviceView!!.checkedItemPosition
        val threads = threadsTextView?.text.toString().trim { it <= ' ' }
        val numThreads = threads.toInt()
        handler?.post {
            if (modelIndex == currentModel && deviceIndex == currentDevice && numThreads == currentNumThreads
            ) {
                return@post
            }
            currentModel = modelIndex as Int
            currentDevice = deviceIndex
            currentNumThreads = numThreads

            // Disable classifier while updating
            if (detector != null) {
                detector!!.close()
                detector = null
            }

            // Lookup names of parameters.
            val modelString = modelStrings[modelIndex!!]
            val device = deviceStrings[deviceIndex]
            LOGGER.i("Changing model to $modelString device $device")

            // Try to load model.
            try {
                detector = DetectorFactory.getDetector(assets, modelString)
                // Customize the interpreter to the type of device we want to use.
                if (detector == null) {
                    return@post
                }
            } catch (e: IOException) {
                e.printStackTrace()
                LOGGER.e(e, "Exception in updateActiveModel()")
                val toast = Toast.makeText(
                    applicationContext,
                    "Classifier could not be initialized",
                    Toast.LENGTH_SHORT
                )
                toast.show()
                finish()
            }
            if (device == "CPU") {
                detector!!.useCPU()
            } else if (device == "GPU") {
                detector!!.useGpu()
            } else if (device == "NNAPI") {
                detector!!.useNNAPI()
            }
            detector!!.setNumThreads(numThreads)
            val cropSize = detector!!.inputSize
            croppedBitmap = Bitmap.createBitmap(
                cropSize,
                cropSize,
                Bitmap.Config.ARGB_8888
            )
            frameToCropTransform = ImageUtils.getTransformationMatrix(
                previewWidth, previewHeight,
                cropSize, cropSize,
                sensorOrientation!!, MAINTAIN_ASPECT
            )
            cropToFrameTransform = Matrix()
            frameToCropTransform?.invert(cropToFrameTransform)
        }
    }

    override fun processImage() { // 이미지 처리. 카메라 정보를 가져오는 부분
        ++timestamp
        val currTimestamp = timestamp
        trackingOverlay!!.postInvalidate()

        // No mutex needed as this method is not reentrant.
        if (computingDetection) {
            readyForNextImage()
            return
        }
        computingDetection = true
        LOGGER.i("Preparing image $currTimestamp for detection in bg thread.")
        getRgbBytes()?.let { rgbFrameBitmap!!.setPixels(it, 0, previewWidth, 0, 0, previewWidth, previewHeight) }
        readyForNextImage()

        // 카메라에서 캡처한 이미지의 rgb 바이트 배열을 가져와 rgbframebitmap에 설정.
        // frametocroptransform 으로 이미지를 자른다.
        val canvas = Canvas(croppedBitmap!!)
        canvas.drawBitmap(rgbFrameBitmap!!, frameToCropTransform!!, null)
        // For examining the actual TF input.
        if (SAVE_PREVIEW_BITMAP) {
            ImageUtils.saveBitmap(croppedBitmap)
        }
        runInBackground {
            LOGGER.i("Running detection on image $currTimestamp")
            val startTime = SystemClock.uptimeMillis()
            val results: List<Classifier.Recognition> =
                detector!!.recognizeImage(croppedBitmap) // 객체 감지 실행하는 부분
            lastProcessingTimeMs = SystemClock.uptimeMillis() - startTime
            Log.e("CHECK", "run: " + results.size)
            cropCopyBitmap = Bitmap.createBitmap(croppedBitmap!!)
            val canvas = Canvas(cropCopyBitmap!!)
            val paint = Paint()
            paint.color = Color.RED
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 2.0f
            var minimumConfidence = MINIMUM_CONFIDENCE_TF_OD_API
            minimumConfidence = when (MODE) {
                DetectorMode.TF_OD_API -> MINIMUM_CONFIDENCE_TF_OD_API
            }
            val mappedRecognitions: MutableList<Classifier.Recognition> =
                LinkedList()
            for (result in results) {
                val location = result.location
                if (location != null && result.confidence >= minimumConfidence) {
                    canvas.drawRect(location, paint)
                    cropToFrameTransform!!.mapRect(location)
                    result.location = location
                    mappedRecognitions.add(result)
                }
            }
            tracker!!.trackResults(mappedRecognitions, currTimestamp)
            trackingOverlay!!.postInvalidate()
            computingDetection = false
            runOnUiThread {
                showFrameInfo(previewWidth.toString() + "x" + previewHeight)
                showCropInfo(cropCopyBitmap!!.width.toString() + "x" + cropCopyBitmap!!.height)
                showInference(lastProcessingTimeMs.toString() + "ms")
            }
        }
    }


    override fun getLayoutId(): Int {
        return R.layout.yawn_od
    }



    override fun getDesiredPreviewFrameSize(): Size {
        return DESIRED_PREVIEW_SIZE
    }


    // Which detection model to use: by default uses Tensorflow Object Detection API frozen
    // checkpoints.
    private enum class DetectorMode {
        TF_OD_API
    }

    override fun setUseNNAPI(isChecked: Boolean) {
        runInBackground { detector!!.setUseNNAPI(isChecked) }
    }

    override fun setNumThreads(numThreads: Int) {
        runInBackground { detector!!.setNumThreads(numThreads) }
    }



    companion object {
        private val LOGGER = Logger()
        private val MODE = DetectorMode.TF_OD_API
        private const val MINIMUM_CONFIDENCE_TF_OD_API = 0.3f
        private const val MAINTAIN_ASPECT = true
        private val DESIRED_PREVIEW_SIZE = Size(640, 640)
        private const val SAVE_PREVIEW_BITMAP = false
        private const val TEXT_SIZE_DIP = 10f
    }

}