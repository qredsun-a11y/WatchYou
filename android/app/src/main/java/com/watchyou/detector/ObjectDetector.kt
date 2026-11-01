package com.watchyou.detector

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.DataType
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

class ObjectDetector(private val context: Context) {

    companion object {
        private const val TAG = "ObjectDetector"
        private const val MODEL_PATH = "yolov5n_int8.tflite"
        private const val INPUT_SIZE = 640
        private const val CONFIDENCE_THRESHOLD = 0.5f
        private const val IOU_THRESHOLD = 0.45f
        private val TARGET_CLASSES = setOf(0, 14, 15, 16, 17, 18, 19)
        private val CLASS_NAMES = mapOf(
            0 to "person", 14 to "bird", 15 to "cat", 16 to "dog",
            17 to "horse", 18 to "sheep", 19 to "cow"
        )
    }

    private var interpreter: Interpreter? = null
    private var inputShape: IntArray = intArrayOf(1, INPUT_SIZE, INPUT_SIZE, 3)
    private var outputShape: IntArray = intArrayOf(1, 25200, 85)
    private var inputSizeBytes: Int = 1 * INPUT_SIZE * INPUT_SIZE * 3 * 4 // float32 = 4 bytes

    init {
        loadModel()
    }

    private fun loadModelFile(path: String): ByteBuffer {
        val bytes = ByteArrayOutputStream()
        context.assets.open(path).use { input ->
            val buffer = ByteArray(8192)
            var bytesRead: Int
            while (input.read(buffer).also { bytesRead = it } != -1) {
                bytes.write(buffer, 0, bytesRead)
            }
        }
        val byteBuffer = ByteBuffer.allocateDirect(bytes.size())
        byteBuffer.order(ByteOrder.nativeOrder())
        byteBuffer.put(bytes.toByteArray())
        byteBuffer.rewind()
        return byteBuffer
    }

    private fun loadModel() {
        try {
            val options = Interpreter.Options().apply {
                numThreads = 2
                useNNAPI = false
            }
            interpreter = Interpreter(loadModelFile(MODEL_PATH), options)

            // Use known model shapes from Python inspection
            // Input: [1, 640, 640, 3] float32
            // Output: [1, 25200, 85] float32
            inputShape = intArrayOf(1, INPUT_SIZE, INPUT_SIZE, 3)
            outputShape = intArrayOf(1, 25200, 85)
            inputSizeBytes = inputShape[1] * inputShape[2] * inputShape[3] * 4

            Log.i(TAG, "Model loaded. inputShape=${inputShape.contentToString()}, inputSizeBytes=$inputSizeBytes, outputShape=${outputShape.contentToString()}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load model", e)
        }
    }

    fun detect(bitmap: Bitmap): List<DetectionResult> {
        val interpreter = this.interpreter ?: return emptyList()
        val results = mutableListOf<DetectionResult>()

        try {
            val scaled = Bitmap.createScaledBitmap(bitmap, INPUT_SIZE, INPUT_SIZE, true)
            val inputBuffer = fillInputBuffer(scaled)

            // Verify input buffer size matches expected
            if (inputBuffer.capacity() != inputSizeBytes) {
                Log.e(TAG, "Input buffer size mismatch: got ${inputBuffer.capacity()}, expected $inputSizeBytes")
                scaled.recycle()
                return emptyList()
            }

            // Use ByteBuffer for output (TensorBuffer has type resolution issues)
            val outputSizeBytes = outputShape[1] * outputShape[2] * 4
            val outputByteBuffer = ByteBuffer.allocateDirect(outputSizeBytes)
            outputByteBuffer.order(ByteOrder.nativeOrder())

            Log.d(TAG, "Running inference...")
            interpreter.run(inputBuffer, outputByteBuffer)
            Log.d(TAG, "Inference complete")

            // Rewind buffer before reading (run() changes position)
            outputByteBuffer.rewind()
            val output = outputByteBuffer.asFloatBuffer()
            val numDetections = outputShape[1].toInt()
            val elementsPerDetection = outputShape[2].toInt()

            Log.d(TAG, "Output: numDetections=$numDetections, elementsPerDetection=$elementsPerDetection, totalElements=${output.capacity()}")

            for (i in 0 until numDetections) {
                val baseOffset = i * elementsPerDetection
                // YOLOv5 output format: [x_center, y_center, width, height, confidence, class_0...class_79]
                val xCenter = output.get(baseOffset + 0)
                val yCenter = output.get(baseOffset + 1)
                val width = output.get(baseOffset + 2)
                val height = output.get(baseOffset + 3)
                val confidence = output.get(baseOffset + 4)

                if (confidence >= CONFIDENCE_THRESHOLD) {
                    // Find class with max probability
                    var maxClassProb = 0f
                    var classId = -1
                    for (c in 0 until 80) {
                        val prob = output.get(baseOffset + 5 + c)
                        if (prob > maxClassProb) {
                            maxClassProb = prob
                            classId = c
                        }
                    }

                    if (classId in TARGET_CLASSES && maxClassProb > 0) {
                        val x = xCenter * INPUT_SIZE
                        val y = yCenter * INPUT_SIZE
                        val w = width * INPUT_SIZE
                        val h = height * INPUT_SIZE
                        val x1 = (x - w / 2).toInt().coerceAtLeast(0)
                        val y1 = (y - h / 2).toInt().coerceAtLeast(0)
                        val x2 = (x + w / 2).toInt().coerceAtMost(INPUT_SIZE)
                        val y2 = (y + h / 2).toInt().coerceAtMost(INPUT_SIZE)
                        results.add(DetectionResult(
                            className = CLASS_NAMES[classId] ?: "unknown",
                            confidence = confidence,
                            x1 = x1, y1 = y1, x2 = x2, y2 = y2
                        ))
                    }
                }
            }
            scaled.recycle()
        } catch (e: Exception) {
            Log.e(TAG, "Detection failed", e)
        }
        return results
    }

    private fun fillInputBuffer(bitmap: Bitmap): ByteBuffer {
        val buffer = ByteBuffer.allocateDirect(inputSizeBytes)
        buffer.order(ByteOrder.nativeOrder())
        buffer.rewind()

        val pixels = IntArray(INPUT_SIZE * INPUT_SIZE)
        bitmap.getPixels(pixels, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE)

        // NHWC format: [1, H, W, 3] - write R, G, B for each pixel
        for (pixel in pixels) {
            buffer.putFloat((pixel shr 16 and 0xFF) / 255.0f) // R
            buffer.putFloat((pixel shr 8 and 0xFF) / 255.0f)  // G
            buffer.putFloat((pixel and 0xFF) / 255.0f)       // B
        }
        buffer.rewind()
        Log.d(TAG, "Input buffer filled: ${buffer.capacity()} bytes")
        return buffer
    }

    fun close() {
        interpreter?.close()
        interpreter = null
    }
}


