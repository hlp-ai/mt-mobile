package com.yimt.asr;

import static com.yimt.asr.TensorUtils.createFloatTensor;
import static com.yimt.asr.TensorUtils.createIntTensor;
import static com.yimt.asr.TensorUtils.tensorShape;

import ai.onnxruntime.*;

import java.util.HashMap;
import java.util.Map;


public class Whisper implements AutoCloseable {
    private OrtSession session; // 定义私有属性session，用于存储ONNX Runtime会话
    private Map<String, OnnxTensor> baseInputs; // 定义私有属性baseInputs，用于存储模型的输入张量

    // 构造函数，接受模型字节数组作为参数
    public Whisper(byte[] modelByte) throws OrtException{
        OrtEnvironment env = OrtEnvironment.getEnvironment(); // 获取ONNX Runtime环境
        OrtSession.SessionOptions sessionOptions = new OrtSession.SessionOptions(); // 创建会话选项

        try{
            session = env.createSession(modelByte, sessionOptions); // 使用环境创建会话
        }
        catch (OrtException e)
        {
            e.printStackTrace();
        }

        long nMels = 80; // 设置nMels值为80
        long nFrames = 3000; // 设置nFrames值为3000

        // TODO: 输入签名不同
//        // 初始化模型的基本输入，这些输入在每次推理时保持不变
        baseInputs = new HashMap<>();
//        baseInputs.put("min_length", createIntTensor(env, new int[]{1}, tensorShape(1)));
//        baseInputs.put("max_length", createIntTensor(env, new int[]{200}, tensorShape(1)));
//        baseInputs.put("num_beams", createIntTensor(env, new int[]{1}, tensorShape(1)));
//        baseInputs.put("num_return_sequences", createIntTensor(env, new int[]{1}, tensorShape(1)));
//        baseInputs.put("length_penalty", createFloatTensor(env, new float[]{1.0f}, tensorShape(1)));
//        baseInputs.put("repetition_penalty", createFloatTensor(env, new float[]{1.0f}, tensorShape(1)));
    }

    // 定义名为run的方法，用于执行推理
    public String run(OnnxTensor audioTensor)throws OrtException {
        Map<String, OnnxTensor> inputs = new HashMap<>(baseInputs); // 创建一个映射，用于存储模型的输入，并复制基本输入
//        inputs.put("audio_pcm", audioTensor); // 将音频张量添加到输入映射中

        OrtSession.Result outputs = session.run(inputs); // 执行推理并获取输出

        // TODO: 输出签名不同
        String recognizedText = outputs.get("str").toString(); // 获取识别到的文本结果

        return recognizedText;
    }

    // 实现AutoCloseable接口的close方法，用于关闭会话和释放资源
    @Override
    public void close() throws OrtException{
        for (OnnxTensor tensor : baseInputs.values()) {
            tensor.close(); // 关闭基本输入的所有张量
        }
        session.close(); // 关闭会话
    }
}
