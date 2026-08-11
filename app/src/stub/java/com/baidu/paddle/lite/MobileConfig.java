package com.baidu.paddle.lite;

/**
 * Paddle Lite MobileConfig 的本地 stub。
 * 当真实 PaddlePredictor.jar 不存在时参与编译，运行时抛异常以触发 ML Kit 降级。
 */
public class MobileConfig {
    public void setModelFromFile(String modelPath) {
        throw new UnsupportedOperationException("Paddle Lite native library is missing");
    }

    public void setPowerMode(PowerMode powerMode) {
        throw new UnsupportedOperationException("Paddle Lite native library is missing");
    }

    public void setThreads(int threads) {
        throw new UnsupportedOperationException("Paddle Lite native library is missing");
    }
}
