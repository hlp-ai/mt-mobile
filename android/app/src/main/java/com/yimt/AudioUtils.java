package com.yimt;

import android.app.Activity;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.util.Base64;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

public class AudioUtils {

    private MediaRecorder mediaRecorder = null;
    public String audioFile = null;  // 录音文件

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

    //开始录音
    public String startRecording(Activity context) throws IOException {
        File dir = new File(context.getExternalFilesDir(null), "audio");
        if (!dir.exists())
            dir.mkdirs();

        File soundFile = new File(dir, System.currentTimeMillis() + ".amr");
        if (!soundFile.exists())
            soundFile.createNewFile();

        mediaRecorder = new MediaRecorder();
        mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.AMR_WB);
        mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_WB);
        audioFile = soundFile.getAbsolutePath(); // 使用 soundFile 的路径
        mediaRecorder.setOutputFile(audioFile);

        mediaRecorder.prepare();
        mediaRecorder.start();

        return audioFile;
    }

    // 停止录音
    public void stopRecording() {
        if(mediaRecorder != null){
            mediaRecorder.stop();
            mediaRecorder.reset();
            mediaRecorder.release();
            mediaRecorder = null;
        }
    }
}
