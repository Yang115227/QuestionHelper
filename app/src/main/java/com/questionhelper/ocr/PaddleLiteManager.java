package com.questionhelper.ocr;  // ← 修改这里

import android.content.Context;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.util.Log;

import com.baidu.paddle.lite.MobileConfig;
import com.baidu.paddle.lite.PaddlePredictor;
import com.baidu.paddle.lite.Tensor;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Paddle Lite OCR 管理器
 * 负责模型加载、推理和资源释放
 */
public class PaddleLiteManager {
    private static final String TAG = "PaddleLiteManager";
    private PaddlePredictor predictor;
    private final Context context;
    private boolean isInitialized = false;

    public PaddleLiteManager(Context context) {
        this.context = context.getApplicationContext();
    }

    /**
     * 初始化预测器（从 assets 复制模型到缓存目录）
     */
    public synchronized boolean init(String modelPath, String labelPath) {
        if (isInitialized) {
            return true;
        }
        try {
            // 将模型从 assets 复制到缓存目录
            String cacheDir = context.getCacheDir().getAbsolutePath();
            String realModelPath = copyAssetToCache(modelPath, cacheDir);
            String realLabelPath = copyAssetToCache(labelPath, cacheDir);

            MobileConfig config = new MobileConfig();
            config.setModelFromFile(realModelPath);
            predictor = PaddlePredictor.createPaddlePredictor(config);

            isInitialized = true;
            Log.i(TAG, "PaddleLite init success");
            return true;
        } catch (Exception e) {
            Log.e(TAG, "PaddleLite init failed", e);
            isInitialized = false;
            return false;
        }
    }

    /**
     * 执行 OCR 推理
     */
    public synchronized List<OcrResult> runOcr(Bitmap bitmap) {
        if (!isInitialized || predictor == null) {
            Log.e(TAG, "Predictor not initialized");
            return new ArrayList<>();
        }
        try {
            // 预处理：将 Bitmap 转为输入 Tensor
            Tensor inputTensor = predictor.getInput(0);
            // ... 具体的预处理逻辑根据你的模型输入尺寸调整
            // 这里保留你原有的预处理代码

            predictor.run();

            Tensor outputTensor = predictor.getOutput(0);
            // ... 后处理：解析输出为文本列表
            // 这里保留你原有的后处理代码

            return parseResults(outputTensor);
        } catch (Exception e) {
            Log.e(TAG, "OCR inference failed", e);
            return new ArrayList<>();
        }
    }

    /**
     * 释放资源
     */
    public synchronized void release() {
        if (predictor != null) {
            try {
                predictor = null;
            } catch (Exception e) {
                Log.e(TAG, "Release failed", e);
            }
        }
        isInitialized = false;
    }

    public boolean isInitialized() {
        return isInitialized;
    }

    // ========== 内部工具方法 ==========

    private String copyAssetToCache(String assetPath, String cacheDir) throws Exception {
        File outFile = new File(cacheDir, new File(assetPath).getName());
        if (outFile.exists()) {
            return outFile.getAbsolutePath();
        }
        AssetManager am = context.getAssets();
        try (InputStream is = am.open(assetPath);
             FileOutputStream fos = new FileOutputStream(outFile)) {
            byte[] buffer = new byte[4096];
            int len;
            while ((len = is.read(buffer)) != -1) {
                fos.write(buffer, 0, len);
            }
            fos.flush();
        }
        return outFile.getAbsolutePath();
    }

    private List<OcrResult> parseResults(Tensor tensor) {
        // 保留你原有的解析逻辑
        return new ArrayList<>();
    }

    /**
     * OCR 结果数据类
     */
    public static class OcrResult {
        public String text;
        public float confidence;
        public float[] bbox; // [x1,y1,x2,y2,x3,y3,x4,y4]

        public OcrResult(String text, float confidence, float[] bbox) {
            this.text = text;
            this.confidence = confidence;
            this.bbox = bbox;
        }
    }
}
