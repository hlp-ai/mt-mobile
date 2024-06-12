package com.yimt;

import android.graphics.Bitmap;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.util.Base64;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;

public class Utils {

    // 播放音频数据
    public static void playAudio(String audio, String type) throws IOException {
        byte[] audioData = Base64.decode(audio, Base64.DEFAULT);

        File tempAudioFile = File.createTempFile("temp_audio", "." + type);

        OutputStream os = new FileOutputStream(tempAudioFile);
        os.write(audioData);
        os.close();

        MediaPlayer mediaPlayer = new MediaPlayer();
        mediaPlayer.setDataSource(tempAudioFile.getAbsolutePath());
        mediaPlayer.prepare();
        mediaPlayer.start();

        mediaPlayer.setOnCompletionListener(mp -> {
            mediaPlayer.release();
            tempAudioFile.delete();
        });
    }

    // 播放音频文件
    public static void playAudio(String audioFile) throws IOException {
        MediaPlayer mediaPlayer = new MediaPlayer();
        mediaPlayer.setDataSource(audioFile);
        mediaPlayer.prepare();
        mediaPlayer.start();

        mediaPlayer.setOnCompletionListener(mp -> {
            mediaPlayer.release();
        });
    }

    // 服务请求
    public static String requestService(String urlString, String data, String method) throws IOException, JSONException {
        int CONN_TIMEOUT = 15000;
        int READ_TIMEOUT = 15000;

        URL url = new URL(urlString);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setConnectTimeout(CONN_TIMEOUT);
        conn.setReadTimeout(READ_TIMEOUT);
        conn.setRequestMethod(method);
        conn.setDoInput(true);

        if(data != null) {
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true);
//            conn.setDoInput(true);

            // 发送请求数据
            OutputStream os = conn.getOutputStream();
            os.write(data.getBytes(StandardCharsets.UTF_8));
            os.close();
        }

        // 获取并处理响应数据
        InputStream is = conn.getInputStream();
        BufferedReader reader = new BufferedReader(new InputStreamReader(is));
        String line;
        StringBuilder response = new StringBuilder();
        while ((line = reader.readLine()) != null) {
            response.append(line);
        }
        reader.close();
        is.close();
        conn.disconnect();

        return response.toString();
    }

    public static JSONObject requestService(String urlString, String data) throws IOException, JSONException {
        return new JSONObject(requestService(urlString, data, "POST"));
    }

//    public static String lang2code(String lang) {
//        if(lang.equals("自动检测"))
//            return "auto";
//        if(lang.equals("中文"))
//            return "zh";
//        if(lang.equals("英文"))
//            return "en";
//        if(lang.equals("日文"))
//            return "ja";
//        if(lang.equals("阿拉伯文"))
//            return "ar";
//
//        return null;
//    }

    public static HashMap<String, String> parseLanguages(String languages){
        String[] parts = languages.split(",");

        HashMap<String, String> result = new HashMap<String, String>();
        result.put("auto", "自动检测");
        for(int i=0; i<parts.length; i++){
            String[] p = parts[i].split("=");
            result.put(p[0], p[1]);
        }

        return result;
    }

    public static String encodeFileToBase64(String filePath) throws IOException {
        byte[] audioData = readFileToByteArray(filePath);
        return Base64.encodeToString(audioData, Base64.DEFAULT);
    }

    public static String encodeAudioFileToBase64(String filePath) throws IOException {
        byte[] audioData = readFileToByteArray(filePath);
        return Base64.encodeToString(audioData, Base64.DEFAULT);
    }

    private static byte[] readFileToByteArray(String filePath) throws IOException {
        try (FileInputStream fileInputStream = new FileInputStream(filePath);
             ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream()) {

            byte[] buffer = new byte[1024];
            int bytesRead;
            while ((bytesRead = fileInputStream.read(buffer)) != -1) {
                byteArrayOutputStream.write(buffer, 0, bytesRead);
            }

            return byteArrayOutputStream.toByteArray();
        }
    }

    public static String encodeImageToBase64(Bitmap imageBitmap) throws IOException {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        imageBitmap.compress(Bitmap.CompressFormat.JPEG, 100, byteArrayOutputStream);
        byte[] imageBytes = byteArrayOutputStream.toByteArray();
        return Base64.encodeToString(imageBytes, Base64.DEFAULT);
    }

}
