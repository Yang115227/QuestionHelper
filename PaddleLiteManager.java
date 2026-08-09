package com.yourapp.ocr;

import android.content.Context;
import android.util.Log;

import com.baidu.paddle.lite.MobileConfig;
import com.baidu.paddle.lite.PaddlePredictor;
import com.baidu.paddle.lite.PowerMode;
import com.baidu.paddle.lite.Tensor;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

/**
 * PaddleLite 管理器 —— 极简工具类模板
 * <p>
 * 负责 PaddleLite 全局初始化、PaddleOCR 三模型（det/rec/cls）的加载与推理调用。
 * 使用前请确保：
 * 1. 已将 PaddlePredictor.jar 放入 app/libs/
 * 2. 已将 libpaddle_light_api_shared.so + libpaddle_lite_jni.so 放入
 * app/src/main/jniLibs/arm64-v8a/
 * 3. 模型文件（.nb 格式）放在 assets/ 或 SD 卡指定目录
 * <p>
 * 用法示例：
 * PaddleLiteManager.getInstance().init(context, modelDirPath);
 * PaddleLiteManager.getInstance().runDet(inputTensor);
 */
public class PaddleLiteManager {

    private static final String TAG = "PaddleLiteManager";

    // ========== 单例 ==========
    private static PaddleLiteManager instance;

    // ========== OCR 三模型预测器 ==========
    private PaddlePredictor detPredictor;  // 文本检测
    private PaddlePredictor recPredictor;  // 文本识别
    private PaddlePredictor clsPredictor;  // 方向分类（可选）

    // ========== 推理配置 ==========
    private static final int NUM_THREADS = 4;
    private static final PowerMode POWER_MODE = PowerMode.LITE_POWER_HIGH;

    // ========== 模型文件名（用户按实际 .nb 文件名修改）==========
    private static final String DET_MODEL = "ch_PP-OCRv3_det.nb";
    private static final String REC_MODEL = "ch_PP-OCRv3_rec.nb";
    private static final String CLS_MODEL = "ch_ppocr_mobile_v2.0_cls.nb";

    private PaddleLiteManager() {
    }

    public static synchronized PaddleLiteManager getInstance() {
        if (instance == null) {
            instance = new PaddleLiteManager();
        }
        return instance;
    }

    // ============================================================
    //  初始化：加载所有模型
    // ============================================================

    /**
     * 初始化 PaddleLite 并加载 OCR 三模型
     *
     * @param context      Android 上下文
     * @param modelDirPath 模型文件所在目录（如 "/data/data/xxx/files/models/"）
     * @return true 全部加载成功
     */
    public boolean init(Context context, String modelDirPath) {
        Log.d(TAG, "PaddleLite init start, modelDir=" + modelDirPath);

        // JNI 库由 PaddlePredictor 的 static 块自动加载，无需手动 System.loadLibrary

        // 依次加载三个模型
        detPredictor = loadModel(modelDirPath + File.separator + DET_MODEL);
        recPredictor = loadModel(modelDirPath + File.separator + REC_MODEL);
        clsPredictor = loadModel(modelDirPath + File.separator + CLS_MODEL);

        boolean allOk = (detPredictor != null) && (recPredictor != null);
        Log.d(TAG, "PaddleLite init completed, det=" + (detPredictor != null)
                + " rec=" + (recPredictor != null)
                + " cls=" + (clsPredictor != null));
        return allOk;
    }

    /**
     * 加载单个 .nb 模型，返回 PaddlePredictor
     */
    private PaddlePredictor loadModel(String modelPath) {
        File modelFile = new File(modelPath);
        if (!modelFile.exists()) {
            Log.w(TAG, "Model file not found: " + modelPath);
            return null;
        }

        MobileConfig config = new MobileConfig();
        config.setModelFromFile(modelPath);
        config.setThreads(NUM_THREADS);
        config.setPowerMode(POWER_MODE);

        PaddlePredictor predictor = PaddlePredictor.createPaddlePredictor(config);
        if (predictor == null) {
            Log.e(TAG, "Failed to create predictor for: " + modelPath);
        }
        return predictor;
    }

    // ============================================================
    //  推理接口（供 OCR pipeline 调用）
    // ============================================================

    /**
     * 执行检测模型推理
     *
     * @param inputData 归一化后的输入数据，shape 为 [1, 3, height, width]
     * @return 检测输出 tensor 的 float 数据
     */
    public float[] runDet(float[] inputData, long[] shape) {
        return runPredictor(detPredictor, "det", inputData, shape);
    }

    /**
     * 执行识别模型推理
     *
     * @param inputData 归一化后的输入数据
     * @return 识别输出 tensor 的 float 数据
     */
    public float[] runRec(float[] inputData, long[] shape) {
        return runPredictor(recPredictor, "rec", inputData, shape);
    }

    /**
     * 执行方向分类模型推理
     *
     * @return 分类输出 tensor 的 float 数据
     */
    public float[] runCls(float[] inputData, long[] shape) {
        return runPredictor(clsPredictor, "cls", inputData, shape);
    }

    /**
     * 通用推理：设置输入 -> 执行 run -> 获取输出
     */
    private float[] runPredictor(PaddlePredictor predictor, String name,
                                 float[] inputData, long[] shape) {
        if (predictor == null) {
            Log.e(TAG, name + " predictor is null, skip inference");
            return null;
        }

        // 获取输入 tensor
        Tensor inputTensor = predictor.getInput(0);
        if (inputTensor == null) {
            Log.e(TAG, name + " getInput(0) failed");
            return null;
        }

        // 设置 shape 并填充数据
        inputTensor.resize(shape);
        inputTensor.setData(inputData);

        // 执行推理
        if (!predictor.run()) {
            Log.e(TAG, name + " predictor.run() failed");
            return null;
        }

        // 获取输出 tensor
        Tensor outputTensor = predictor.getOutput(0);
        if (outputTensor == null) {
            Log.e(TAG, name + " getOutput(0) failed");
            return null;
        }

        return outputTensor.getFloatData();
    }

    // ============================================================
    //  资源释放
    // ============================================================

    /**
     * 释放所有预测器（GC 时会自动调用 finalize 释放 native 指针）
     */
    public void release() {
        detPredictor = null;
        recPredictor = null;
        clsPredictor = null;
        Log.d(TAG, "PaddleLite predictors released");
    }

    // ============================================================
    //  Getter（供外部获取原始 predictor 做更细粒度控制）
    // ============================================================

    public PaddlePredictor getDetPredictor() {
        return detPredictor;
    }

    public PaddlePredictor getRecPredictor() {
        return recPredictor;
    }

    public PaddlePredictor getClsPredictor() {
        return clsPredictor;
    }
}