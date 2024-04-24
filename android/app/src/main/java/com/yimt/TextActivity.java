package com.yimt;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.util.Base64;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import com.google.android.material.snackbar.Snackbar;
import com.yimt.databinding.ActivityTextBinding;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;


public class TextActivity extends AppCompatActivity {

    private ActivityTextBinding binding;
    private String[] languages = new String[] {"自动检测", "中文", "英文"};

    private boolean isRecording = false;
    private MediaRecorder mediaRecorder = null;
    private String audioFile = null;

    @SuppressLint("ClickableViewAccessibility")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityTextBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        ArrayAdapter<String> srcLangAdapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_dropdown_item, languages);
        srcLangAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        binding.spinnerSrcLang.setAdapter(srcLangAdapter);

        ArrayAdapter<String> tgtLangAdapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_dropdown_item, languages);
        tgtLangAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        binding.spinnerTgtLang.setAdapter(tgtLangAdapter);
        binding.spinnerTgtLang.setSelection(1);

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED)
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO}, 200);

        // 话筒按钮: 长按录音
        binding.MicroPhone.setOnLongClickListener(v -> {
            // Toast.makeText(TextActivity.this, "长按", Toast.LENGTH_LONG).show();
            startRecording();
            isRecording = true;

            return true;
        });

        // 话筒按钮: 松开结束
        binding.MicroPhone.setOnTouchListener((view, motionEvent) -> {
            if(motionEvent.getAction() == MotionEvent.ACTION_UP) {
                stopRecording();
                Toast.makeText(TextActivity.this, "录音完成", Toast.LENGTH_LONG).show();
            }

            return false;
        });

//        binding.MicroPhone.setOnClickListener(v -> {
//            if(!isRecording){
//                if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED)
//                    requestRecordAudioPermission();
//
//                startRecording();
//                isRecording = true;
//
//            }else{
//                stopRecording();
//                handler.removeCallbacks(updateRecordingTextRunnable); // 停止更新文本
//                binding.voice.setTextSize(18);
//                binding.voice.setText("录音");
//                isRecording = false;
//                try {
//                    getAudioText(audioFile);
//                } catch (Exception e) {
//                    throw new RuntimeException(e);
//                }
//            }
//        });

        // 删除按钮
        binding.RemoveSourceText.setOnClickListener(view -> {
            if (!binding.textSource.getText().toString().equals("")) {
                binding.textSource.setText("");
                binding.textTarget.setText("");

                InputMethodManager imm = (InputMethodManager) getSystemService(Activity.INPUT_METHOD_SERVICE);
                View view1 = getCurrentFocus();
                if (view1 != null) {
                    imm.hideSoftInputFromWindow(view1.getWindowToken(), 0);
                }
            }

        });

        // 拷贝按钮
        binding.CopyTranslation.setOnClickListener(view -> {
            if (!binding.textTarget.getText().toString().equals("")) {
                ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                ClipData clip = ClipData.newPlainText("translation", binding.textTarget.getText());
                clipboard.setPrimaryClip(clip);
                Snackbar.make(
                        binding.CopyTranslation,
                        getString(R.string.copiedClipboard),
                        Snackbar.LENGTH_LONG
                ).show();
            }
        });

        binding.ReadTranslation.setOnClickListener(v -> {
            playAudio();
        });
    }

    //开始录音
    private void startRecording() {
        if (mediaRecorder == null) {
            try{
                File dir = new File(getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "audio");
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
            }catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    // 停止录音
    private void stopRecording(){
        if (mediaRecorder!=null){
            mediaRecorder.stop();
            mediaRecorder.reset();
            mediaRecorder.release();
            mediaRecorder = null;
        }
    }

    // 播放声音
    private void playAudio(){
        try {
            // Initialize and start the MediaPlayer
            MediaPlayer mediaPlayer = new MediaPlayer();
            File testAudioFile = new File(new File(getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "audio"), "test.amr");
            mediaPlayer.setDataSource(testAudioFile.getAbsolutePath());
            mediaPlayer.prepare();
            mediaPlayer.start();

            // Add an event listener to release resources when playback is complete
            mediaPlayer.setOnCompletionListener(mp -> {
                mediaPlayer.release();
                // tempAudioFile.delete(); // Delete the temporary file
            });
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}