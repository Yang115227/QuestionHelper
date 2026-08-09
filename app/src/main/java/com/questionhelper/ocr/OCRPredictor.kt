package com.questionhelper.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.util.Log
import com.baidu.paddle.lite.MobileConfig
import com.baidu.paddle.lite.PaddlePredictor
import com.baidu.paddle.lite.PowerMode
import com.baidu.paddle.lite.Tensor
import java.io.*
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.*
import kotlin.math.*

class OCRPredictor(context: Context, assetPath: String) {
    private val tag = "OCRPredictor"
    private var detPredictor: PaddlePredictor? = null
    private var recPredictor: PaddlePredictor? = null
    private var clsPredictor: PaddlePredictor? = null
    private val wordLabels = mutableListOf<String>()
    private val context: Context = context.applicationContext
    private val assetPath: String = assetPath

    // 模型配置参数
    private val detInputShape = intArrayOf(1, 3, 480, 480)
    private val recInputShape = intArrayOf(1, 3, 48, 320)
    private val clsInputShape = intArrayOf(1, 3, 48, 192)
    private val mean = floatArrayOf(0.485f, 0.456f, 0.406f)
    private val std = floatArrayOf(0.229f, 0.224f, 0.225f)

    init {
        loadModels()
        loadLabels()
    }

    private fun loadModels() {
        try {
            detPredictor = loadModel("$assetPath/ch_PP-OCRv3_det_infer.nb")
            recPredictor = loadModel("$assetPath/ch_PP-OCRv3_rec_infer.nb")
            clsPredictor = loadModel("$assetPath/ch_ppocr_mobile_v2.0_cls_infer.nb")
            Log.d(tag, "All models loaded")
        } catch (e: Exception) {
            Log.e(tag, "Failed to load models", e)
            throw RuntimeException("模型加载失败: ${e.message}")
        }
    }

    private fun loadModel(modelName: String): PaddlePredictor {
        val modelFile = copyAssetToCache(modelName)
        val config = MobileConfig()
        config.setModelFromFile(modelFile.absolutePath)
        config.setPowerMode(PowerMode.LITE_POWER_HIGH)
        config.setThreads(4)
        return PaddlePredictor.createPaddlePredictor(config)
    }

    private fun copyAssetToCache(assetName: String): File {
        val outFile = File(context.cacheDir, assetName.replace("/", "_"))
        if (outFile.exists()) return outFile
        outFile.parentFile?.mkdirs()
        context.assets.open(assetName).use { input ->
            FileOutputStream(outFile).use { output ->
                input.copyTo(output)
            }
        }
        return outFile
    }

    private fun loadLabels() {
        try {
            context.assets.open("$assetPath/ppocr_keys_v1.txt").bufferedReader().useLines { lines ->
                lines.forEach { wordLabels.add(it) }
            }
            Log.d(tag, "Loaded ${wordLabels.size} labels")
        } catch (e: Exception) {
            Log.e(tag, "Failed to load labels", e)
        }
    }

    fun runOcr(bitmap: Bitmap): List<OcrResult> {
        if (detPredictor == null || recPredictor == null) return emptyList()

        // 1. 文本检测
        val boxes = runDetection(bitmap)
        if (boxes.isEmpty()) return emptyList()

        // 2. 方向分类（可选）
        // 3. 文本识别
        val results = mutableListOf<OcrResult>()
        for (box in boxes) {
            val crop = cropBox(bitmap, box)
            val text = runRecognition(crop)
            if (text.isNotBlank()) {
                results.add(OcrResult(text, box))
            }
            crop.recycle()
        }

        // 按 Y 坐标排序（从上到下）
        return results.sortedBy { it.box.minOf { p -> p.y } }
    }

    private fun runDetection(bitmap: Bitmap): List<List<Point>> {
        val inputTensor = preprocessDet(bitmap)
        val predictor = detPredictor ?: return emptyList()
        predictor.run()

        val outputTensor = predictor.getOutput(0)
        val outputShape = outputTensor.shape()
        val outputData = outputTensor.floatData()

        return postprocessDet(outputData, outputShape, bitmap.width, bitmap.height)
    }

    private fun preprocessDet(bitmap: Bitmap): Tensor {
        val h = detInputShape[2]
        val w = detInputShape[3]
        val scaledBitmap = scaleBitmap(bitmap, w, h)
        val inputData = bitmapToFloatArray(scaledBitmap, h, w, mean, std)
        scaledBitmap.recycle()

        val inputTensor = detPredictor!!.getInput(0)
        inputTensor.resize(detInputShape)
        inputTensor.setData(inputData)
        return inputTensor
    }

    private fun postprocessDet(
        data: FloatArray, shape: LongArray,
        srcWidth: Int, srcHeight: Int
    ): List<List<Point>> {
        val h = shape[2].toInt()
        val w = shape[3].toInt()
        val mask = Array(h) { y ->
            BooleanArray(w) { x ->
                data[y * w + x] > 0.3f
            }
        }

        val boxes = findContours(mask)
        val ratioH = srcHeight.toFloat() / h
        val ratioW = srcWidth.toFloat() / w

        return boxes.map { contour ->
            contour.map { p ->
                Point(
                    (p.x * ratioW).toInt().coerceIn(0, srcWidth - 1),
                    (p.y * ratioH).toInt().coerceIn(0, srcHeight - 1)
                )
            }
        }.filter { box ->
            val area = polygonArea(box)
            area > 10
        }
    }

    private fun runRecognition(bitmap: Bitmap): String {
        val h = recInputShape[2]
        val w = recInputShape[3]
        val scaledBitmap = scaleBitmap(bitmap, w, h)
        val inputData = bitmapToFloatArray(scaledBitmap, h, w, mean, std)
        scaledBitmap.recycle()

        val inputTensor = recPredictor!!.getInput(0)
        inputTensor.resize(recInputShape)
        inputTensor.setData(inputData)
        recPredictor!!.run()

        val outputTensor = recPredictor!!.getOutput(0)
        val outputData = outputTensor.floatData()
        val outputShape = outputTensor.shape()

        return decodeRecOutput(outputData, outputShape)
    }

    private fun decodeRecOutput(data: FloatArray, shape: LongArray): String {
        val numSteps = shape[1].toInt()
        val numClasses = shape[2].toInt()
        val sb = StringBuilder()
        var lastIndex = -1

        for (t in 0 until numSteps) {
            var maxVal = -Float.MAX_VALUE
            var maxIdx = 0
            for (c in 0 until numClasses) {
                val v = data[t * numClasses + c]
                if (v > maxVal) {
                    maxVal = v
                    maxIdx = c
                }
            }
            if (maxIdx != 0 && maxIdx != lastIndex && maxIdx - 1 < wordLabels.size) {
                sb.append(wordLabels[maxIdx - 1])
            }
            lastIndex = maxIdx
        }
        return sb.toString()
    }

    private fun cropBox(bitmap: Bitmap, box: List<Point>): Bitmap {
        val left = box.minOf { it.x }.coerceAtLeast(0)
        val top = box.minOf { it.y }.coerceAtLeast(0)
        val right = box.maxOf { it.x }.coerceAtMost(bitmap.width)
        val bottom = box.maxOf { it.y }.coerceAtMost(bitmap.height)
        val width = (right - left).coerceAtLeast(1)
        val height = (bottom - top).coerceAtLeast(1)
        return Bitmap.createBitmap(bitmap, left, top, width, height)
    }

    private fun scaleBitmap(src: Bitmap, dstW: Int, dstH: Int): Bitmap {
        return Bitmap.createScaledBitmap(src, dstW, dstH, true)
    }

    private fun bitmapToFloatArray(
        bitmap: Bitmap, h: Int, w: Int,
        mean: FloatArray, std: FloatArray
    ): FloatArray {
        val pixels = IntArray(h * w)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
        val floatValues = FloatArray(3 * h * w)

        for (i in pixels.indices) {
            val pixel = pixels[i]
            val r = ((pixel shr 16) and 0xFF) / 255.0f
            val g = ((pixel shr 8) and 0xFF) / 255.0f
            val b = (pixel and 0xFF) / 255.0f
            floatValues[i] = (r - mean[0]) / std[0]
            floatValues[i + h * w] = (g - mean[1]) / std[1]
            floatValues[i + 2 * h * w] = (b - mean[2]) / std[2]
        }
        return floatValues
    }

    private fun findContours(mask: Array<BooleanArray>): List<List<Point>> {
        val h = mask.size
        val w = mask[0].size
        val visited = Array(h) { BooleanArray(w) }
        val boxes = mutableListOf<List<Point>>()
        val dirs = arrayOf(intArrayOf(-1, 0), intArrayOf(1, 0), intArrayOf(0, -1), intArrayOf(0, 1))

        for (y in 0 until h) {
            for (x in 0 until w) {
                if (mask[y][x] && !visited[y][x]) {
                    val contour = mutableListOf<Point>()
                    val queue = ArrayDeque<Point>()
                    queue.add(Point(x, y))
                    visited[y][x] = true

                    while (queue.isNotEmpty()) {
                        val p = queue.removeFirst()
                        contour.add(p)
                        for (d in dirs) {
                            val ny = p.y + d[0]
                            val nx = p.x + d[1]
                            if (ny in 0 until h && nx in 0 until w && mask[ny][nx] && !visited[ny][nx]) {
                                visited[ny][nx] = true
                                queue.add(Point(nx, ny))
                            }
                        }
                    }

                    if (contour.size >= 10) {
                        val rect = minAreaRect(contour)
                        boxes.add(rect)
                    }
                }
            }
        }
        return boxes
    }

    private fun minAreaRect(points: List<Point>): List<Point> {
        val centerX = points.map { it.x }.average()
        val centerY = points.map { it.y }.average()
        val sorted = points.sortedBy { atan2((it.y - centerY), (it.x - centerX)) }
        return sorted
    }

    private fun polygonArea(points: List<Point>): Double {
        var area = 0.0
        for (i in points.indices) {
            val j = (i + 1) % points.size
            area += points[i].x * points[j].y
            area -= points[j].x * points[i].y
        }
        return abs(area) / 2.0
    }

    fun release() {
        detPredictor?.destroy()
        recPredictor?.destroy()
        clsPredictor?.destroy()
    }

    data class Point(val x: Int, val y: Int)
    data class OcrResult(val text: String, val box: List<Point>)
}
