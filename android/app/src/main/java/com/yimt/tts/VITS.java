package com.yimt.tts;

import android.app.Activity;
import android.content.res.AssetManager;
import android.util.Log;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;

public class VITS {

    private static final Map<Character, Integer> symbolToId = new HashMap<>();

    static long[] speakerId = {0};
    static double noiseScale = 0.1;
    static double noiseScaleW = 0.668;
    static long lengthScale = 1;

    static OrtEnvironment env = OrtEnvironment.getEnvironment();

    static {
        symbolToId.put('_', 0);
        symbolToId.put(',', 1);
        symbolToId.put('.', 2);
        symbolToId.put('!', 3);
        symbolToId.put('?', 4);
        symbolToId.put('-', 5);
        symbolToId.put('~', 6);
        symbolToId.put('…', 7);
        symbolToId.put('N', 8);
        symbolToId.put('Q', 9);
        symbolToId.put('a', 10);
        symbolToId.put('b', 11);
        symbolToId.put('d', 12);
        symbolToId.put('e', 13);
        symbolToId.put('f', 14);
        symbolToId.put('g', 15);
        symbolToId.put('h', 16);
        symbolToId.put('i', 17);
        symbolToId.put('j', 18);
        symbolToId.put('k', 19);
        symbolToId.put('l', 20);
        symbolToId.put('m', 21);
        symbolToId.put('n', 22);
        symbolToId.put('o', 23);
        symbolToId.put('p', 24);
        symbolToId.put('s', 25);
        symbolToId.put('t', 26);
        symbolToId.put('u', 27);
        symbolToId.put('v', 28);
        symbolToId.put('w', 29);
        symbolToId.put('x', 30);
        symbolToId.put('y', 31);
        symbolToId.put('z', 32);
        symbolToId.put('ɑ', 33);
        symbolToId.put('æ', 34);
        symbolToId.put('ʃ', 35);
        symbolToId.put('ʑ', 36);
        symbolToId.put('ç', 37);
        symbolToId.put('ɯ', 38);
        symbolToId.put('ɪ', 39);
        symbolToId.put('ɔ', 40);
        symbolToId.put('ɛ', 41);
        symbolToId.put('ɹ', 42);
        symbolToId.put('ð', 43);
        symbolToId.put('ə', 44);
        symbolToId.put('ɫ', 45);
        symbolToId.put('ɥ', 46);
        symbolToId.put('ɸ', 47);
        symbolToId.put('ʊ', 48);
        symbolToId.put('ɾ', 49);
        symbolToId.put('ʒ', 50);
        symbolToId.put('θ', 51);
        symbolToId.put('β', 52);
        symbolToId.put('ŋ', 53);
        symbolToId.put('ɦ', 54);
        symbolToId.put('⁼', 55);
        symbolToId.put('ʰ', 56);
        symbolToId.put('`', 57);
        symbolToId.put('^', 58);
        symbolToId.put('#', 59);
        symbolToId.put('*', 60);
        symbolToId.put('=', 61);
        symbolToId.put('ˈ', 62);
        symbolToId.put('ˌ', 63);
        symbolToId.put('→', 64);
        symbolToId.put('↓', 65);
        symbolToId.put('↑', 66);
        symbolToId.put(' ', 67);
    }

    public static void saveArrayAsWav(float[] data, String filePath) {
        try {
            // 创建WAV文件头
            byte[] header = createWavHeader(data.length);

            // 创建文件
            File file = new File(filePath);
            FileOutputStream fos = new FileOutputStream(file);

            // 写入WAV文件头
            fos.write(header);

            // 写入数据
            for (float value : data) {
                fos.write(toByteArray(value));
            }

            fos.close();
        } catch (IOException e) {
            Log.e("MainActivity", "An IOException occurred", e);
        }
    }

    private static byte[] createWavHeader(int dataLength) {
        int sampleRate = 22050;
        int channels = 1;
        int bytesPerSample = 2; // 2 bytes per sample (float)

        int totalLength = dataLength * bytesPerSample + 44;

        byte[] header = new byte[44];

        // RIFF chunk descriptor
        header[0] = 'R';
        header[1] = 'I';
        header[2] = 'F';
        header[3] = 'F';
        writeInt(header, 4, totalLength);
        header[8] = 'W';
        header[9] = 'A';
        header[10] = 'V';
        header[11] = 'E';

        // Format chunk
        header[12] = 'f';
        header[13] = 'm';
        header[14] = 't';
        header[15] = ' ';
        writeInt(header, 16, 16); // chunk size
        header[20] = 1; // audio format (1 = PCM)
        header[22] = (byte) channels;
        writeInt(header, 24, sampleRate);
        writeInt(header, 28, sampleRate * channels * bytesPerSample); // Byte rate
        header[32] = (byte) (channels * bytesPerSample); // Block align
        header[34] = (byte) (bytesPerSample * 8); // Bits per sample

        // Data chunk
        header[36] = 'd';
        header[37] = 'a';
        header[38] = 't';
        header[39] = 'a';
        writeInt(header, 40, dataLength * bytesPerSample);

        return header;
    }

    private static byte[] toByteArray(float value) {
        byte[] bytes = new byte[2];
        int intValue = (int) (value * 32767.0f); // 将float值转换为16位整数
        bytes[0] = (byte) intValue;
        bytes[1] = (byte) (intValue >> 8);
        return bytes;
    }

    private static void writeInt(byte[] buffer, int offset, int value) {
        buffer[offset] = (byte) (value & 0xFF);
        buffer[offset + 1] = (byte) ((value >> 8) & 0xFF);
        buffer[offset + 2] = (byte) ((value >> 16) & 0xFF);
        buffer[offset + 3] = (byte) ((value >> 24) & 0xFF);
    }

    public static String cleantext(String text) {
        return text;

    }

    public static float[] audioCreate(String text, Activity context) throws OrtException, IOException {
//        text = text.replace("\n", "").replace("\r", "").replace(" ", "");
//
//        List<Long> list = toSequence(text);
//        //long[] x = list.stream().mapToLong(Long::valueOf).toArray();
//
//        long[] input_x = new long[list.size() * 2 + 1];
//
//        for (int i = 0, j = 0; i < input_x.length * 2 + 1; i++) {
//            if (i % 2 == 0) {
//                input_x[i] = 0;
//            } else {
//                //input_x[i] = x[j++];
//                input_x[i] = list.get(j).longValue();
//                j++;
//            }
//        }

        long[] input_x = {0, 22, 0, 17, 0, 65, 0, 66, 0, 30, 0, 33, 0, 48, 0, 65, 0, 66, 0, 1, 0, 67, 0, 25, 0, 57, 0, 42, 0, 57, 0, 65, 0, 26, 0, 35, 0, 55, 0, 17, 0, 41, 0, 65, 0, 3, 0};

        OrtSession session = init(context);

        Log.i("TTS", "Init Done");

        float[] audio = vits(input_x, session);

        return audio;
    }

    public static OrtSession init(Activity context) throws IOException, OrtException {
        AssetManager assetManager = context.getAssets();
        InputStream stream = assetManager.open("G_trilingual.onnx");
        ByteArrayOutputStream byteStream = new ByteArrayOutputStream();

        byte[] buffer = new byte[4096];
        int bytesRead;
        while ((bytesRead = stream.read(buffer)) != -1) {
            byteStream.write(buffer, 0, bytesRead);
        }

        byteStream.flush();
        byte[] bytes = byteStream.toByteArray();
        Log.i("TTS", String.valueOf(bytes.length));

        OrtSession session = env.createSession(bytes, new OrtSession.SessionOptions());

        return session;
    }

    public static float[] vits(long[] input_x, OrtSession session) throws OrtException {
        long[][] x = {input_x};
        OnnxTensor t_x = OnnxTensor.createTensor(env, x);
        long[] x_lengths = {x[0].length};
        OnnxTensor t_x_lengths = OnnxTensor.createTensor(env, x_lengths);
        long[] sid = speakerId;
        OnnxTensor t_sid = OnnxTensor.createTensor(env, sid);
        double[] noise_scale = {noiseScale};
        OnnxTensor t_noise_scale = OnnxTensor.createTensor(env, noise_scale);
        double[] noise_scale_w = {noiseScaleW};
        OnnxTensor t_noise_scale_w = OnnxTensor.createTensor(env, noise_scale_w);
        long[] length_scale = {lengthScale};
        OnnxTensor t_x_length_scale = OnnxTensor.createTensor(env, length_scale);
        Map<String, OnnxTensor> inputs = new HashMap<>();
        inputs.put("x", t_x);
        inputs.put("x_lengths", t_x_lengths);
        inputs.put("sid", t_sid);
        inputs.put("noise_scale", t_noise_scale);
        inputs.put("length_scale", t_x_length_scale);
        inputs.put("noise_scale_w", t_noise_scale_w);

        Log.i("TTS", "Infering...");

        try (OrtSession.Result results = session.run(inputs)) {
            float[][][] result = (float[][][]) results.get(0).getValue();
            float[] audio = result[0][0];
            //saveArrayAsWav(audio, wavFilePath);
            return audio;
        }
    }

    public List<Long> toSequence(String text) {

        List<Long> sequence = new ArrayList<>();

        text = cleantext(text);

        for (char symbol : text.toCharArray()) {
            if (!symbolToId.containsKey(symbol)) {
                continue;
            }
            int symbolId = symbolToId.get(symbol);
            sequence.add((long) symbolId);
        }

        return sequence;

    }
}
