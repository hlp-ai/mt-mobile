package com.yimt;

import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.util.Base64;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

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
