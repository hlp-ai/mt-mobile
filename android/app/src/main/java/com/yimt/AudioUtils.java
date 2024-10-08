package com.yimt;

import android.app.Activity;
import android.content.Context;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.os.Environment;
import android.util.Base64;
import android.util.Log;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

public class AudioUtils {

    private MediaRecorder mediaRecorder = null;
    public String audioFile = null;  // 录音文件

    // 采样率，现在能够保证在所有设备上使用的采样率是44100Hz, 但是其他的采样率（22050, 16000, 11025）在一些设备上也可以使用。
    public static final int SAMPLE_RATE_INHZ = 16000;

    // 声道数。CHANNEL_IN_MONO and CHANNEL_IN_STEREO. 其中CHANNEL_IN_MONO是可以保证在所有设备能够使用的。
    public static final int CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO;

    // 返回的音频数据的格式。 ENCODING_PCM_8BIT, ENCODING_PCM_16BIT, and ENCODING_PCM_FLOAT.
    public static final int AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT;

    private static final String TAG = "YiMT";
    //private final Context context;

    private AudioRecord audioRecord;

    public String audioCacheFilePath;
    private String wavFilePath;

    /**
     * 录音的工作线程
     */
    private Thread recordingAudioThread;
    private boolean isRecording = false;//mark if is recording

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

    public String startRecordAudio() {
        audioCacheFilePath = Environment.getExternalStorageDirectory().getAbsolutePath();
        audioCacheFilePath += "/jerboa_audio_cache.pcm";
        //wav文件的路径放在系统的音频目录下

        // 获取最小录音缓存大小，
        int minBufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE_INHZ, CHANNEL_CONFIG, AUDIO_FORMAT);
        AudioRecord audioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC, SAMPLE_RATE_INHZ, CHANNEL_CONFIG, AUDIO_FORMAT, minBufferSize);

        isRecording = true;
        audioRecord.startRecording();

        // 创建数据流，将缓存导入数据流
        recordingAudioThread = new Thread(new Runnable() {
            @Override
            public void run() {
                File file = new File(audioCacheFilePath);
                Log.i(TAG, "PCM文件路径: " + audioCacheFilePath);

                if (file.exists())
                    file.delete();

               try {
                   file.createNewFile();

                   FileOutputStream fos = new FileOutputStream(file);

                   byte[] data = new byte[minBufferSize];
                   int read;

                   while (isRecording && !recordingAudioThread.isInterrupted()) {
                       read = audioRecord.read(data, 0, minBufferSize);
                       if (AudioRecord.ERROR_INVALID_OPERATION != read) {
                           fos.write(data);
                           Log.i("audioRecordTest", "写录音数据->" + read);
                       }
                   }

                   // 关闭数据流
                   fos.close();
               }
               catch (IOException e){
                   e.printStackTrace();
               }
            }
        });

        recordingAudioThread.start();

        return audioCacheFilePath;
    }

    public String stopRecordAudio() {
        try {
            this.isRecording = false;
            if (this.audioRecord != null) {
                this.audioRecord.stop();
                this.audioRecord.release();
                this.audioRecord = null;

                this.recordingAudioThread.interrupt();
                this.recordingAudioThread = null;
            }

            return audioCacheFilePath;
        } catch (Exception e) {
            Log.w(TAG, e.getLocalizedMessage());

            return null;
        }
    }
}
