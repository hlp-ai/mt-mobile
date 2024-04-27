package com.yimt;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.provider.MediaStore;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import com.google.android.material.snackbar.Snackbar;
import com.yimt.databinding.ActivityMainBinding;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

import javax.net.ssl.HttpsURLConnection;


public class MainActivity extends AppCompatActivity {

    private ActivityMainBinding binding;

    private Handler mhandler;
    private static final int TRANSLATE_MSG =  201;

    private final static String DEFAULT_SERVER = "http://192.168.1.104:5555";
    final static int CONN_TIMEOUT = 15000;
    final static int READ_TIMEOUT = 15000;

    private String[] languages = new String[] {"自动检测", "中文", "英文"};

    private MediaRecorder mediaRecorder = null;
    private String audioFile = null;
    private Uri imageUri = null;

    private static final int REQUEST_CHOOSE_IMAGE = 101;
    private static final int REQUEST_CROP_IMAGE = 102;
    private static final int REQUEST_IMAGE_CAPTURE = 103;
    private static final int REQUEST_WRITE_STORAGE = 104;

    private static final int REQUEST_CAMERA_PERMISSION = 200;

    @SuppressLint("ClickableViewAccessibility")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        mhandler = new Handler(Looper.getMainLooper()) {
            public void handleMessage(Message msg) {
                super.handleMessage(msg);

                if (msg.what == TRANSLATE_MSG) {
                    Bundle lc = msg.getData();
                    String translation = lc.getString("translation");
                    String serverError = lc.getString("error");
                    if (translation.isEmpty() && serverError.length() > 0)
                        Toast.makeText(MainActivity.this, serverError, Toast.LENGTH_LONG).show();
                    binding.textTarget.setText(translation);
                    //binding.translationPending.setVisibility(View.GONE);
                }
            }
        };

        ArrayAdapter<String> srcLangAdapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_dropdown_item, languages);
        srcLangAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        binding.spinnerSrcLang.setAdapter(srcLangAdapter);

        ArrayAdapter<String> tgtLangAdapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_dropdown_item, languages);
        tgtLangAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        binding.spinnerTgtLang.setAdapter(tgtLangAdapter);
        binding.spinnerTgtLang.setSelection(1);

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED)
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO}, 200);

        // 翻译按钮
        binding.StartTranslation.setOnClickListener(view -> {
            if (!binding.textSource.getText().toString().equals("")) {
                translateText();
                // binding.translationPending.setVisibility(View.VISIBLE);
            }

        });

        // 话筒按钮: 长按录音
        binding.MicroPhone.setOnLongClickListener(v -> {
            // Toast.makeText(TextActivity.this, "长按", Toast.LENGTH_LONG).show();
            startRecording();
            return true;
        });

        // 话筒按钮: 松开结束
        binding.MicroPhone.setOnTouchListener((view, motionEvent) -> {
            if(motionEvent.getAction() == MotionEvent.ACTION_UP) {
                stopRecording();
                Toast.makeText(MainActivity.this, "录音完成", Toast.LENGTH_LONG).show();
            }

            return false;
        });

        // 相机按钮
        binding.Camera.setOnClickListener(view -> {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED)
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.CAMERA},
                        REQUEST_CAMERA_PERMISSION);

            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED)
            {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                        REQUEST_WRITE_STORAGE);
            }

            Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            if (takePictureIntent.resolveActivity(getPackageManager()) != null) {
                ContentValues values = new ContentValues();
                values.put(MediaStore.Images.Media.TITLE, "New Picture");
                values.put(MediaStore.Images.Media.DESCRIPTION, "From Camera");
                imageUri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
                takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, imageUri);
                startActivityForResult(takePictureIntent, REQUEST_IMAGE_CAPTURE);
            }
        });

        // 相册按钮
        binding.Gallery.setOnClickListener(view -> {
            Intent pickIntent = new Intent(Intent.ACTION_PICK, null);
            pickIntent.setDataAndType(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, "image/*");
            startActivityForResult(pickIntent, REQUEST_CHOOSE_IMAGE);
        });

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

        // 分享按钮
        binding.Share.setOnClickListener(view -> {
            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("text/plain");
            shareIntent.putExtra(Intent.EXTRA_TEXT, "分享测试");
            startActivity(Intent.createChooser(shareIntent, "分享翻译"));
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

        // 播放按钮
        binding.ReadTranslation.setOnClickListener(v -> {
            playAudio();
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_CHOOSE_IMAGE && resultCode == RESULT_OK) {  // 成功从相册选择图片
            imageUri = data.getData();
            Toast.makeText(MainActivity.this, imageUri.toString(), Toast.LENGTH_LONG).show();
            crop(imageUri);
        } else if (requestCode == REQUEST_CROP_IMAGE && resultCode == RESULT_OK) {  // 成功剪切图片
            Toast.makeText(MainActivity.this, "CROP Image Done", Toast.LENGTH_LONG).show();
            imageUri = data.getData();
        } else if (requestCode == REQUEST_IMAGE_CAPTURE && resultCode == RESULT_OK) {  // 成功拍照
            Toast.makeText(MainActivity.this, "CAPTURE DONE", Toast.LENGTH_LONG).show();
            crop(imageUri);
        } else {
            super.onActivityResult(requestCode, resultCode, data);
        }
    }

    private void crop(Uri imageUri) {
        Intent intent = new Intent("com.android.camera.action.CROP");
        //Uri uri = Uri.parse(MediaStore.Images.Media.insertImage(TextActivity.this.getContentResolver(), bitmap, "1", "1"));
        intent.setDataAndType(imageUri, "image/*");//设置要缩放的图片Uri和类型
        intent.putExtra("crop", "true");
        intent.putExtra("scale", true);//缩放
        intent.putExtra("return-data", false);//当为true的时候就返回缩略图，false就不返回，需要通过Uri
        intent.putExtra("noFaceDetection", false);//前置摄像头
        startActivityForResult(intent, REQUEST_CROP_IMAGE);//打开剪裁Activity
    }

    private String requestTranslate(String server, String apiKey) throws Exception {
        String translation = "";

        URL url = new URL(server + "/translate");
        Log.d("yimt", "Translate using " + url);

        HttpURLConnection conn = null;
        try {
            conn = server.startsWith("https") ?
                    (HttpsURLConnection) url.openConnection() : (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(CONN_TIMEOUT);
            conn.setReadTimeout(READ_TIMEOUT);
            conn.setRequestMethod("POST");
            conn.setRequestProperty("accept", "application/json");

            String q = binding.textSource.getText().toString().replace("&", "%26");
            String source = "en"; // binding.spinnerSrcLang.getSelectedItem().toString();
            String target = "zh";  // binding.spinnerTgtLang.getSelectedItem().toString();
            String data = "q=" + q + "&source=" + source + "&target=" + target;
            if (!apiKey.equals(""))
                data += "&api_key=" + apiKey;

            byte[] out = data.getBytes(StandardCharsets.UTF_8);
            OutputStream stream = conn.getOutputStream();
            stream.write(out);

            InputStream inputStream = new DataInputStream(conn.getInputStream());
            BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
            translation = new JSONObject(reader.readLine()).getString("translatedText");
        } finally {
            conn.disconnect();
        }

        return translation;
    }

    private void translateText() {
        if (!binding.textSource.getText().toString().equals("")) {
            String server = DEFAULT_SERVER;
            String apiKey = "";

            Thread thread = new Thread(() -> {
                String translation = "";
                String error = "";
                try {
                    translation = requestTranslate(server, apiKey);
                } catch (Exception e) {
                    e.printStackTrace();
                    error = e.toString();
                }

                Bundle bundle = new Bundle();
                bundle.putString("translation", translation);
                bundle.putString("error", error);
                Message msg = new Message();
                msg.setData(bundle);
                msg.what = TRANSLATE_MSG;
                mhandler.sendMessage(msg);
            });

            thread.start();
        }
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