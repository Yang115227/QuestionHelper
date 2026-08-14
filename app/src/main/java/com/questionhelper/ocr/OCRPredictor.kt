package com.questionhelper.ocr

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.baidu.paddle.lite.MobileConfig
import com.baidu.paddle.lite.PaddlePredictor
import com.baidu.paddle.lite.PowerMode
import com.baidu.paddle.lite.Tensor
import java.io.*
import kotlin.math.*

class OCRPredictor(context: Context, assetPath: String) {
    private val tag = "OCRPredictor"
    private var detPredictor: PaddlePredictor? = null
    private var recPredictor: PaddlePredictor? = null
    private var clsPredictor: PaddlePredictor? = null
    private val wordLabels = mutableListOf<String>()
    private val context: Context = context.applicationContext
    private val assetPath: String = assetPath

    // 调试开关：true 表示跳过检测，直接识别整张图；false 恢复正常流程
    private val DEBUG_SKIP_DET = true

    // 用于存储调试信息，OcrManager 可以读取
    var debugInfo: String? = null
        private set

    private val detInputShape = intArrayOf(1, 3, 480, 480)
    private val recInputShape = intArrayOf(1, 3, 48, 320)

    // 检测模型归一化参数（ImageNet 标准）
    private val detMean = floatArrayOf(0.485f, 0.456f, 0.406f)
    private val detStd = floatArrayOf(0.229f, 0.224f, 0.225f)

    // 识别模型归一化参数（PaddleOCR 官方）
    private val recMean = floatArrayOf(0.5f, 0.5f, 0.5f)
    private val recStd = floatArrayOf(0.5f, 0.5f, 0.5f)

    init {
        Log.d(tag, "OCRPredictor 开始初始化")
        loadModels()
        loadLabels()
        Log.d(tag, "OCRPredictor 初始化完成")
    }

    private fun loadModels() {
        detPredictor = loadModel("det", "$assetPath/ch_PP-OCRv3_det_infer_opt.nb")
        recPredictor = loadModel("rec", "$assetPath/ch_PP-OCRv3_rec_infer_opt.nb")
        clsPredictor = loadModel("cls", "$assetPath/ch_ppocr_mobile_v2.0_cls_infer_opt.nb")
    }

    private fun loadModel(name: String, modelName: String): PaddlePredictor {
        Log.d(tag, "[$name] 开始加载模型: $modelName")
        val modelFile = copyAssetToCache(modelName)
        Log.d(tag, "[$name] 模型缓存路径: ${modelFile.absolutePath}, 大小=${modelFile.length()} bytes")

        val config = MobileConfig()
        config.setModelFromFile(modelFile.absolutePath)
        config.setPowerMode(PowerMode.LITE_POWER_HIGH)
        config.setThreads(4)

        Log.d(tag, "[$name] 调用 createPaddlePredictor...")
        val predictor = PaddlePredictor.createPaddlePredictor(config)
        if (predictor == null) {
            throw RuntimeException("[$name] createPaddlePredictor 返回 null！模型文件可能损坏或与 Paddle Lite 版本不匹配")
        }
        Log.d(tag, "[$name] 模型加载成功")
        return predictor
    }

    private fun copyAssetToCache(assetName: String): File {
        val outFile = File(context.cacheDir, assetName.replace("/", "_"))
        if (outFile.exists()) {
            return outFile
        }
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
            context.assets.open("$assetPath/ppocr_keys_v1.txt")
                .bufferedReader(Charsets.UTF_8)
                .useLines { lines ->
                    lines.forEach { line ->
                        val trimmed = line.trim()
                        if (trimmed.isNotEmpty()) {
                            wordLabels.add(trimmed)
                        }
                    }
                }
            Log.d(tag, "标签加载完成，共 ${wordLabels.size} 个")
            Log.d(tag, "前10个标签: ${wordLabels.take(10).joinToString(",")}")
        } catch (e: Exception) {
            Log.e(tag, "标签加载失败", e)
        }
    }

    fun runOcr(bitmap: Bitmap): List<OcrResult> {
        val rec = recPredictor ?: return emptyList()

        if (DEBUG_SKIP_DET) {
            // 调试模式：直接识别整张图
            val text = runRecognition(bitmap, rec)
            if (text.isNotBlank()) {
                val box = listOf(
                    Point(0, 0),
                    Point(bitmap.width, 0),
                    Point(bitmap.width, bitmap.height),
                    Point(0, bitmap.height)
                )
                return listOf(OcrResult(text, box))
            }
            return emptyList()
        }

        // 正常流程：检测 + 识别
        val det = detPredictor ?: return emptyList()
        val boxes = runDetection(bitmap, det)
        if (boxes.isEmpty()) return emptyList()

        val results = mutableListOf<OcrResult>()
        for (box in boxes) {
            val crop = cropBox(bitmap, box)
            val text = runRecognition(crop, rec)
            if (text.isNotBlank()) {
                results.add(OcrResult(text, box))
            }
            crop.recycle()
        }
        return results.sortedBy { it.box.minOf { p -> p.y } }
    }

    private fun runDetection(bitmap: Bitmap, predictor: PaddlePredictor): List<List<Point>> {
        val h = detInputShape[2]
        val w = detInputShape[3]
        val scaledBitmap = Bitmap.createScaledBitmap(bitmap, w, h, true)
        val inputData = bitmapToFloatArray(scaledBitmap, h, w, detMean, detStd)
        scaledBitmap.recycle()

        val inputTensor = predictor.getInput(0)
        inputTensor.resize(detInputShape.map { it.toLong() }.toLongArray())
        inputTensor.setData(inputData)
        predictor.run()

        val outputTensor = predictor.getOutput(0)
        val outputShape = outputTensor.shape()
        val outputData = outputTensor.getFloatData()

        return postprocessDet(outputData, outputShape, bitmap.width, bitmap.height)
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
        }.filter { polygonArea(it) > 10 }
    }

    private fun runRecognition(bitmap: Bitmap, predictor: PaddlePredictor): String {
        val resizedBitmap = resizeAndPad(bitmap, recInputShape[3], recInputShape[2])
        val inputData = bitmapToFloatArray(resizedBitmap, recInputShape[2], recInputShape[3], recMean, recStd)
        resizedBitmap.recycle()

        val inputTensor = predictor.getInput(0)
        inputTensor.resize(recInputShape.map { it.toLong() }.toLongArray())
        inputTensor.setData(inputData)
        predictor.run()

        val outputTensor = predictor.getOutput(0)
        val outputData = outputTensor.getFloatData()
        val outputShape = outputTensor.shape()

        // 生成调试信息
        val shapeStr = outputShape.joinToString("x")
        val numSteps = outputShape[1].toInt()
        val numClasses = outputShape[2].toInt()
        val maxIndices = IntArray(minOf(10, numSteps)) { t ->
            var maxIdx = 0
            var maxVal = -Float.MAX_VALUE
            for (c in 0 until numClasses) {
                val v = outputData[t * numClasses + c]
                if (v > maxVal) {
                    maxVal = v
                    maxIdx = c
                }
            }
            maxIdx
        }
        val maxIdxStr = maxIndices.joinToString(",")
        debugInfo = "字典=${wordLabels.size}, 输出形状=$shapeStr, 前10步最大索引=[$maxIdxStr]"
        Log.d(tag, debugInfo!!)

        return decodeRecOutput(outputData, outputShape)
    }

    private fun resizeAndPad(src: Bitmap, targetW: Int, targetH: Int): Bitmap {
        val srcW = src.width
        val srcH = src.height
        val scale = min(targetW.toFloat() / srcW, targetH.toFloat() / srcH)
        val newW = (srcW * scale).toInt()
        val newH = (srcH * scale).toInt()

        val scaled = Bitmap.createScaledBitmap(src, newW, newH, true)

        val outBitmap = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(outBitmap)
        canvas.drawColor(android.graphics.Color.BLACK)   // 黑色填充
        val left = (targetW - newW) / 2
        val top = (targetH - newH) / 2
        canvas.drawBitmap(scaled, left.toFloat(), top.toFloat(), null)
        scaled.recycle()
        return outBitmap
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
                        boxes.add(minAreaRect(contour))
                    }
                }
            }
        }
        return boxes
    }

    private fun minAreaRect(points: List<Point>): List<Point> {
        val centerX = points.map { it.x }.average()
        val centerY = points.map { it.y }.average()
        return points.sortedBy { atan2((it.y - centerY), (it.x - centerX)) }
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
        detPredictor = null
        recPredictor = null
        clsPredictor = null
    }

    data class Point(val x: Int, val y: Int)
    data class OcrResult(val text: String, val box: List<Point>)
}