package com.baidu.paddle.lite;

/**
 * Paddle Lite Tensor 的本地 stub。
 * 当真实 PaddlePredictor.jar 不存在时参与编译，运行时抛异常以触发 ML Kit 降级。
 */
public class Tensor {
    public long[] shape() {
        throw new UnsupportedOperationException("Paddle Lite native library is missing");
    }

    public float[] getFloatData() {
        throw new UnsupportedOperationException("Paddle Lite native library is missing");
    }

    public void resize(long[] shape) {
        throw new UnsupportedOperationException("Paddle Lite native library is missing");
    }

    public void setData(float[] data) {
        throw new UnsupportedOperationException("Paddle Lite native library is missing");
    }
}
