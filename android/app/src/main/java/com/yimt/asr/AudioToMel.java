package com.yimt.asr;

import static com.yimt.asr.TensorUtils.tensorShape;

import android.content.Context;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;

import java.nio.FloatBuffer;
import java.util.concurrent.atomic.AtomicBoolean;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;


public class AudioToMel {

    private static final int BYTES_PER_FLOAT = 4;// 每个浮点数占用的字节数
    private static final int SAMPLE_RATE = 16000;// 采样率
    private static final int MAX_AUDIO_LENGTH_IN_SECONDS = 30;// 最大音频长度（秒）
    private static Context context;

    public AudioToMel(Context context) {
        AudioToMel.context = context;
    }

    public static OnnxTensor fromRecording(AtomicBoolean stopRecordingFlag) throws OrtException {// 定义从录音中创建 OnnxTensor 的方法
//        int recordingChunkLengthInSeconds = 1;// 录音块长度（秒）
//
//        // 计算录音缓冲区大小
//        int minBufferSize = Math.max(
//                AudioRecord.getMinBufferSize(// 取两者中较大的值
//                        SAMPLE_RATE,// 采样率
//                        AudioFormat.CHANNEL_IN_MONO,// 单声道
//                        AudioFormat.ENCODING_PCM_FLOAT// PCM 浮点编码
//                ),
//                2 * recordingChunkLengthInSeconds * SAMPLE_RATE * BYTES_PER_FLOAT
//        );
//
//
//        AudioRecord audioRecord = new AudioRecord.Builder()
//                .setAudioSource(MediaRecorder.AudioSource.MIC)// 设置音频源为麦克风
//                .setAudioFormat(
//                        new AudioFormat.Builder()
//                                .setSampleRate(SAMPLE_RATE)// 设置采样率
//                                .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)// 设置编码格式为 PCM 浮点编码
//                                .setChannelMask(AudioFormat.CHANNEL_IN_MONO)// 设置通道掩码为单声道
//                                .build()
//                )
//                .setBufferSizeInBytes(minBufferSize)// 设置缓冲区大小
//                .build();
//
//        try {
//            float[] floatAudioData = new float[MAX_AUDIO_LENGTH_IN_SECONDS * SAMPLE_RATE]; // 创建浮点音频数据数组
//            int floatAudioDataOffset = 0; // 浮点音频数据偏移量
//
//            audioRecord.startRecording();// 开始录音
//
//            // 循环读取录音数据直到停止录音标志被设置或达到最大录音长度
//            while (!stopRecordingFlag.get() && floatAudioDataOffset < floatAudioData.length) {
//                int numFloatsToRead = Math.min(// 取两者中较小的值
//                        recordingChunkLengthInSeconds * SAMPLE_RATE, // 录音块中的样本数
//                        floatAudioData.length - floatAudioDataOffset// 剩余空间中可容纳的样本数
//                );
//
//                // 读取录音数据到 floatAudioData 数组
//                int readResult = audioRecord.read( // 读取录音数据
//                        floatAudioData,
//                        floatAudioDataOffset,
//                        numFloatsToRead,
//                        AudioRecord.READ_BLOCKING// 目标数组、偏移量、要读取的样本数,阻塞方式读取
//                );
//
//                // 检查读取结果
//                if (readResult >= 0) {
//                    floatAudioDataOffset += readResult; // 更新浮点音频数据偏移量
//                } else {
//                    throw new RuntimeException("AudioRecord.read() returned error code " + readResult);// 抛出运行时异常
//                }
//            }
//            // 停止录音
//            audioRecord.stop();
//
//            // 创建 OnnxTensor
//            OrtEnvironment env = OrtEnvironment.getEnvironment();
//            FloatBuffer floatAudioDataBuffer = FloatBuffer.wrap(floatAudioData);
//
//            OnnxTensor originTensor = OnnxTensor.createTensor(env, floatAudioDataBuffer, tensorShape(1, floatAudioData.length));
//
//            return originTensor;
//
//        } finally {
//            if (audioRecord.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING) {
//                audioRecord.stop();// 停止录音
//            }
//            audioRecord.release();// 释放 AudioRecord 资源
//        }
        return null;
    }
}