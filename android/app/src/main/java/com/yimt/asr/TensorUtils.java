package com.yimt.asr;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;


public class TensorUtils {

    // 创建整型数据的 ONNX 张量
    public static OnnxTensor createIntTensor(OrtEnvironment env, int[] data, long[] shape) throws OrtException {
        // 使用 OnnxTensor 的静态方法 createTensor() 创建整型张量，传入环境、数据和形状
        return OnnxTensor.createTensor(env, IntBuffer.wrap(data), shape);
    }

    // 创建浮点型数据的 ONNX 张量
    public static OnnxTensor createFloatTensor(OrtEnvironment env, float[] data, long[] shape) throws OrtException {
        // 使用 OnnxTensor 的静态方法 createTensor() 创建浮点型张量，传入环境、数据和形状
        return OnnxTensor.createTensor(env, FloatBuffer.wrap(data), shape);
    }

    // 用于方便构造张量形状的数组
    public static long[] tensorShape(long... dims) {
        return dims;
    }
}