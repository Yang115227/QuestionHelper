package com.baidu.paddle.lite;

/**
 * Paddle Lite PaddlePredictor 的本地 stub。
 * 当真实 PaddlePredictor.jar 不存在时参与编译，运行时抛异常以触发 ML Kit 降级。
 */
public class PaddlePredictor {
    public static PaddlePredictor createPaddlePredictor(MobileConfig config) {
        throw new UnsupportedOperationException("Paddle Lite native library is missing");
    }

    public Tensor getInput(int index) {
        throw new UnsupportedOperationException("Paddle Lite native library is missing");
    }

    public Tensor getOutput(int index) {
        throw new UnsupportedOperationException("Paddle Lite native library is missing");
    }

    public boolean run() {
        throw new UnsupportedOperationException("Paddle Lite native library is missing");
    }
}
