package com.yimt;

import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.util.Base64;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

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
    public static JSONObject requestService(String urlString, String data) throws IOException, JSONException {
        int CONN_TIMEOUT = 15000;
        int READ_TIMEOUT = 15000;

        URL url = new URL(urlString);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setConnectTimeout(CONN_TIMEOUT);
        conn.setReadTimeout(READ_TIMEOUT);
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setDoOutput(true);
        conn.setDoInput(true);

        // 发送请求数据
        OutputStream os = conn.getOutputStream();
        os.write(data.getBytes(StandardCharsets.UTF_8));
        os.close();

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

        return new JSONObject(response.toString());
    }

    public static String lang2code(String lang) {
        if(lang.equals("自动检测"))
            return "auto";
        if(lang.equals("中文"))
            return "zh";
        if(lang.equals("英文"))
            return "en";

        return null;
    }

//    // 开始录制声音
//    public static String startRecording(File soundFile) throws IOException {
//        if (!soundFile.exists())
//            soundFile.createNewFile();
//
//        MediaRecorder mr = new MediaRecorder();
//        mr.setAudioSource(MediaRecorder.AudioSource.MIC);
//        mr.setOutputFormat(MediaRecorder.OutputFormat.AMR_WB);
//        mr.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_WB);
//        String audioFile = soundFile.getAbsolutePath(); // 使用 soundFile 的路径
//
//        mr.setOutputFile(audioFile);
//        mr.prepare();
//        mr.start();
//
//        return audioFile;
//    }
}
