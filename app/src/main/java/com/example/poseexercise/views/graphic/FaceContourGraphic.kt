package com.example.poseexercise.views.graphic

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import com.example.poseexercise.views.graphic.GraphicOverlay.Graphic
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceLandmark

/** Graphic instance for rendering face contours graphic overlay view. */
class FaceContourGraphic(overlay: GraphicOverlay?) : Graphic(overlay) {
    private val facePositionPaint: Paint = Paint().apply {
        style = Paint.Style.FILL
    }
    private val idPaint: Paint = Paint().apply {
        textSize = ID_TEXT_SIZE
    }
    private val boxPaint: Paint = Paint().apply {
        style = Paint.Style.STROKE
        strokeWidth = BOX_STROKE_WIDTH
    }

    @Volatile
    private var face: Face? = null

    init {
        currentColorIndex = (currentColorIndex + 1) % COLOR_CHOICES.size
        val selectedColor = COLOR_CHOICES[currentColorIndex]
        facePositionPaint.color = selectedColor
        idPaint.color = selectedColor
        boxPaint.color = selectedColor
    }

    /**
     * Updates the face instance from the detection of the most recent frame. Invalidates the relevant
     * portions of the overlay to trigger a redraw.
     */
    fun updateFace(face: Face?) {
        this.face = face
        postInvalidate()
    }

    /** Draws the face annotations for position on the supplied canvas. */
    override fun draw(canvas: Canvas) {
        val face = face ?: return

        // Draw bounding box around the face.
        val bounds = face.boundingBox
        canvas.drawRect(
            translateX(bounds.left.toFloat()),
            translateY(bounds.top.toFloat()),
            translateX(bounds.right.toFloat()),
            translateY(bounds.bottom.toFloat()),
            boxPaint
        )

        // Draw facial landmarks.
        drawLandmarks(canvas)

        // Draw ID text below face.
        canvas.drawText(
            "id: ${face.trackingId}",
            translateX(bounds.centerX().toFloat()),
            translateY(bounds.exactCenterY().toFloat()) + ID_Y_OFFSET,
            idPaint
        )
    }

    /** Draws facial landmarks such as eyes, mouth, and nose. */
    private fun drawLandmarks(canvas: Canvas) {
        face?.allLandmarks?.forEach { landmark ->
            val px = translateX(landmark.position.x)
            val py = translateY(landmark.position.y)
            canvas.drawCircle(px, py, LANDMARK_RADIUS, facePositionPaint)

            // Additional text based on the type of landmark.
            when (landmark.landmarkType) {
                FaceLandmark.LEFT_EYE, FaceLandmark.RIGHT_EYE -> {
                    val eyeOpenProbability = if (landmark.landmarkType == FaceLandmark.LEFT_EYE)
                        face?.leftEyeOpenProbability else face?.rightEyeOpenProbability
                    eyeOpenProbability?.let {
                        canvas.drawText(
                            "Eye open: ${String.format("%.2f", it)}",
                            px,
                            py - ID_Y_OFFSET,
                            idPaint
                        )
                    }
                }
            }
        }
    }

    companion object {
        private const val LANDMARK_RADIUS = 10.0f
        private const val ID_TEXT_SIZE = 40.0f
        private const val ID_Y_OFFSET = 50.0f
        private const val BOX_STROKE_WIDTH = 8.0f
        private val COLOR_CHOICES = intArrayOf(
            Color.BLUE, Color.CYAN, Color.GREEN, Color.MAGENTA, Color.RED, Color.WHITE, Color.YELLOW
        )
        private var currentColorIndex = 0
    }
}