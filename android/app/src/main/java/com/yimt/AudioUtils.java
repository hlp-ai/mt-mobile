package com.yimt;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.os.Environment;
import android.util.Base64;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

public class AudioUtils {

    // 采样率，现在能够保证在所有设备上使用的采样率是44100Hz, 但是其他的采样率（22050, 16000, 11025）在一些设备上也可以使用。
    public static final int SAMPLE_RATE_INHZ = 16000;

    // 声道数。CHANNEL_IN_MONO and CHANNEL_IN_STEREO. 其中CHANNEL_IN_MONO是可以保证在所有设备能够使用的。
    public static final int CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO;

    // 返回的音频数据的格式。 ENCODING_PCM_8BIT, ENCODING_PCM_16BIT, and ENCODING_PCM_FLOAT.
    public static final int AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT;

    private static final String TAG = "YiMT";

    private AudioRecord audioRecord = null;

    private Thread recordingAudioThread;
    private boolean isRecording = false;

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

    public void startRecordAudio(String recordFile) {
        if(isRecording)
            return;

        isRecording = true;

        recordingAudioThread = new Thread(new Runnable() {
            @Override
            public void run() {
                Log.i(TAG, "开始录音");

                // 获取最小录音缓存大小，
                int minBufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE_INHZ,
                        CHANNEL_CONFIG, AUDIO_FORMAT);
                audioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC, SAMPLE_RATE_INHZ, CHANNEL_CONFIG, AUDIO_FORMAT, minBufferSize);

                audioRecord.startRecording();

                File file = new File(recordFile);
                Log.i(TAG, "PCM文件路径: " + recordFile);

               try(FileOutputStream fos = new FileOutputStream(file)) {
                   byte[] data = new byte[minBufferSize];
                   int read;

                   while (isRecording && !recordingAudioThread.isInterrupted()) {
                       read = audioRecord.read(data, 0, minBufferSize);
                       if (AudioRecord.ERROR_INVALID_OPERATION != read && read>0) {
                           fos.write(data, 0, read);
                           Log.i("audioRecordTest", "写录音数据->" + read);
                       }
                   }

                   Log.i(TAG, "结束录音");
               }
               catch (IOException e){
                   e.printStackTrace();
               }
            }
        });

        recordingAudioThread.start();
    }

    public void stopRecordAudio() {
        try {
            this.isRecording = false;

            if (this.audioRecord != null) {
                this.audioRecord.stop();
                this.audioRecord.release();
                this.audioRecord = null;

                this.recordingAudioThread.interrupt();
                this.recordingAudioThread = null;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
