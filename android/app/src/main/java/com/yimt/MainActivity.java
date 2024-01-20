package com.yimt;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.provider.MediaStore;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Base64;
import android.util.Log;
import android.util.TypedValue;
import android.view.MenuInflater;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.PopupMenu;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import android.media.MediaPlayer;
import android.media.MediaRecorder;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import com.google.android.material.snackbar.Snackbar;
//import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions;
//import com.google.mlkit.vision.text.devanagari.DevanagariTextRecognizerOptions;
//import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions;
//import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions;
//import com.google.mlkit.vision.text.latin.TextRecognizerOptions;
import com.yimt.databinding.ActivityMainBinding;
//import com.yimt.ocr.TextRecognitionProcessor;
import com.yimt.ocr.BitmapUtils;
//import com.yimt.ocr.VisionImageProcessor;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import javax.net.ssl.HttpsURLConnection;


public class MainActivity extends AppCompatActivity {
    final static int CONN_TIMEOUT = 15000;
    final static int READ_TIMEOUT = 15000;
    final static int LANGUAGES = 1;
    final static int TRANSLATE = 2;
    final static int TextRecog = 3;
    final static int AudioText = 4;
    final static int AudioPlay = 5;
    private final static String DEFAULT_SERVER = "http://192.168.1.104:5555";
    private static final int REQUEST_IMAGE_CAPTURE = 1001;
    private static final int REQUEST_CHOOSE_IMAGE = 1002;
    private static final int REQUEST_CROP_IMAGE = 1003;
    private static final String TAG = "MainActivity";
    private static final String TEXT_RECOGNITION_LATIN = "Text Recognition Latin"; //en
    private static final String TEXT_RECOGNITION_CHINESE = "Text Recognition Chinese (Beta)"; //zh
    private static final String TEXT_RECOGNITION_DEVANAGARI = "Text Recognition Devanagari (Beta)"; //sa
    private static final String TEXT_RECOGNITION_JAPANESE = "Text Recognition Japanese (Beta)"; //ja
    private static final String TEXT_RECOGNITION_KOREAN = "Text Recognition Korean (Beta)"; //ko
    private final String AUTO_LANG_CODE = "auto";
    private final String AUTO_LANG_NAME = "AutoDetect";
    private SharedPreferences settings;
    private ActivityMainBinding binding;
    private Handler mhandler;
    private String sourceLangCode = AUTO_LANG_CODE;
    private String targetLangCode = "zh";

    private Uri imageUri;
    //    private VisionImageProcessor imageProcessor;
    private String selectedMode = TEXT_RECOGNITION_LATIN;
    private boolean isStart = false;
    private MediaRecorder mr = null;
    private String audioFile = null;
    private MediaPlayer mediaPlayer = null;
    private static final String IS_REQUESTING_PERMISSION = "is_requesting_permission";
    private boolean isRequestingPermission = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "Main onCreate");

        MainActivity activity = this;
        settings = getSharedPreferences("com.yimt", 0);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        if (savedInstanceState != null) {
            isRequestingPermission = savedInstanceState.getBoolean(IS_REQUESTING_PERMISSION, false);
        }

//        createImageProcessor();

        binding.voice.setOnClickListener(v -> {
            if(!isStart){
                startRecord();
                handler.post(updateRecordingTextRunnable); // 开始更新文本
                isStart = true;

                // 设置按钮的 autoSizeTextType 属性为 uniform，并设置最小和最大文本大小
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    binding.voice.setAutoSizeTextTypeWithDefaults(TextView.AUTO_SIZE_TEXT_TYPE_UNIFORM);
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    binding.voice.setAutoSizeTextTypeUniformWithConfiguration(11, 18, 1, TypedValue.COMPLEX_UNIT_SP);
                }
            }else{
                stopRecord();
                handler.removeCallbacks(updateRecordingTextRunnable); // 停止更新文本
                binding.voice.setTextSize(18);
                binding.voice.setText("录音");
                isStart = false;
                try {
                    getAudioText(audioFile);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }

                // 设置按钮的 autoSizeTextType 属性为 none，并将文本大小设置回原始大小
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    binding.voice.setAutoSizeTextTypeWithDefaults(TextView.AUTO_SIZE_TEXT_TYPE_NONE);
                }
                binding.voice.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
            }
        });

        findViewById(R.id.PlayAudioButton).setOnClickListener(v -> {
            String t = binding.TranslatedTV.getText().toString();
            // 如果t为空，则提示用户输入文本
            if (t.isEmpty()) {
                Toast.makeText(this, "没有可播放的文本", Toast.LENGTH_LONG).show();
                return;
            }
            getAudio(t);
        });

        findViewById(R.id.playAudio).setOnClickListener(v -> {
            String t = binding.SourceText.getText().toString();
            if (t.isEmpty()) {
                Toast.makeText(this, "没有可播放的文本", Toast.LENGTH_LONG).show();
                return;
            }
            getAudio(t);
        });

        // OCR button
        findViewById(R.id.select_image_button)
                .setOnClickListener(
                        view -> {
                            // 检查源语言是否为自动检测
                            if (sourceLangCode.equals(AUTO_LANG_CODE)) {
                                // 如果是，先弹出语言选择对话框
                                Toast.makeText(this, "请先指定语言再进行图片识别", Toast.LENGTH_LONG).show();
                                chooseLang(true);
                            } else {
                                // Menu for selecting either: a) take new photo b) select from existing
                                PopupMenu popup = new PopupMenu(MainActivity.this, view);
                                popup.setOnMenuItemClickListener(
                                    menuItem -> {
                                        int itemId = menuItem.getItemId();
                                        if (itemId == R.id.select_images_from_local) {
                                            startChooseImageIntentForResult();
                                            return true;
                                        } else if (itemId == R.id.take_photo_using_camera) {
                                            startCameraIntentForResult();
                                            return true;
                                        }
                                        return false;
                                    });
                                MenuInflater inflater = popup.getMenuInflater();
                                inflater.inflate(R.menu.camera_button_menu, popup.getMenu());
                                popup.show();
                            }
                        });

        try {
            retrieveLanguages();
        } catch (Exception e) {
            e.printStackTrace();
        }

        String languages = settings.getString("languages", "");

        if (!languages.equals("")) {
            sourceLangCode = settings.getString("Source", AUTO_LANG_CODE);
            setSourceLang();
            targetLangCode = settings.getString("Target", "zh");
            setTargetLang();
        }

        mhandler = new Handler(Looper.getMainLooper()) {
            public void handleMessage(Message msg) {
                super.handleMessage(msg);

                if (msg.what == TRANSLATE) {
                    Bundle lc = msg.getData();
                    String transString = lc.getString("transString");
                    String serverError = lc.getString("serverError");
                    if (transString.isEmpty() && serverError.length() > 0)
                        Toast.makeText(activity, serverError, Toast.LENGTH_LONG).show();
                    binding.TranslatedTV.setText(transString);
                    binding.translationPending.setVisibility(View.GONE);
                } else if (msg.what == LANGUAGES) {
                    Bundle lc = msg.getData();
                    String languages = lc.getString("languages");
                    String serverError = lc.getString("serverError");
                    if (languages.isEmpty() && serverError.length() > 0) {
                        Toast.makeText(activity, serverError, Toast.LENGTH_LONG).show();
                    } else {
                        //Setting languages needs to happen before setSourceLang and setTargetLang
                        settings.edit()
                                .putString("languages", languages)
                                .apply();

                        setSourceLang();
                        setTargetLang();
                    }
                } else if (msg.what == TextRecog) {
                    Bundle data = msg.getData();
                    String text = (String) data.get("ocr_text");
                    String translatedText = (String) data.get("translatedText");
                    binding.SourceText.setText(text);
                    binding.TranslatedTV.setText(translatedText);
                }else if (msg.what == AudioText){
                    Bundle data = msg.getData();
                    String text = (String) data.get("audioToText");
                    binding.SourceText.setText((text));
                }else if (msg.what == AudioPlay){
                    Bundle data = msg.getData();
                    String audio = (String) data.get("audio");
                    //使用MediaPlayer播放base64格式的音频
                    String type = (String) data.get("type");
                    playAudio(audio,type);
                }
            }
        };

        // translate on input, disabled now
        boolean translate_on_input = false;
        if (translate_on_input) {
            binding.SourceText.addTextChangedListener(new TextWatcher() {
                private static final int DELAY_MILLIS = 4000;
                final Handler handler = new Handler(Looper.getMainLooper());
                final Runnable workRunnable = () -> translateText();

                @Override
                public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                }

                @Override
                public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                }

                @Override
                public void afterTextChanged(Editable editable) {
                    handler.removeCallbacks(workRunnable);
                    handler.postDelayed(workRunnable, DELAY_MILLIS);
                    binding.translationPending.setVisibility(View.VISIBLE);
                    if (editable.toString().equals(""))
                        binding.translationPending.setVisibility(View.GONE);
                }
            });
        }

        // Translate button
        binding.StartTranslation.setOnClickListener(view -> {
            if (!binding.SourceText.getText().toString().equals("")) {
                translateText();
                binding.translationPending.setVisibility(View.VISIBLE);
            }

        });

        // Remove button
        binding.RemoveSourceText.setOnClickListener(view -> {
            if (!binding.SourceText.getText().toString().equals("")) {
                binding.SourceText.setText("");
                binding.TranslatedTV.setText("");

                InputMethodManager imm = (InputMethodManager) getSystemService(Activity.INPUT_METHOD_SERVICE);
                View view1 = getCurrentFocus();
                if (view1 != null) {
                    imm.hideSoftInputFromWindow(view1.getWindowToken(), 0);
                }
            }

        });

        // Copy button
        binding.CopyTranslation.setOnClickListener(view -> {
            if (!binding.TranslatedTV.getText().toString().equals("")) {
                ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                ClipData clip = ClipData.newPlainText("translated text", binding.TranslatedTV.getText());
                clipboard.setPrimaryClip(clip);
                Snackbar.make(
                        binding.CopyTranslation,
                        getString(R.string.copiedClipboard),
                        Snackbar.LENGTH_LONG
                ).show();
            }
        });

        binding.CopySourceText.setOnClickListener(view -> {
            if (!binding.SourceText.getText().toString().equals("")) {
                ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                ClipData clip = ClipData.newPlainText("source text", binding.SourceText.getText());
                clipboard.setPrimaryClip(clip);
                Snackbar.make(
                        binding.CopyTranslation,
                        getString(R.string.copiedClipboard),
                        Snackbar.LENGTH_LONG
                ).show();
            }
        });

        // Switch language button
        binding.SwitchLanguages.setOnClickListener(view -> {
            String cacheLang = sourceLangCode;
            sourceLangCode = targetLangCode;
            setSourceLang();
            if (cacheLang.equals(AUTO_LANG_CODE))
                cacheLang = "en";
            targetLangCode = cacheLang;
            setTargetLang();
            translateText();
        });

        // Choose source language button
        binding.SourceLanguageBot.setOnClickListener(view -> chooseLang(true));

        // Choose target language button
        binding.TargetLanguageBot.setOnClickListener(view -> chooseLang(false));

        // Settings dialog
        binding.info.setOnClickListener(view -> {
            View about = getLayoutInflater().inflate(R.layout.about, null);
            EditText serverET = about.findViewById(R.id.Server);
            EditText apiET = about.findViewById(R.id.Api);
            Spinner spinner = about.findViewById(R.id.spinner_ocr);
            final String[] server = {settings.getString("server", DEFAULT_SERVER)};
            String apiKey = settings.getString("apiKey", "");
            serverET.setText(server[0]);
            apiET.setText(apiKey);
            AlertDialog.Builder popUp = new AlertDialog.Builder(activity, R.style.AlertDialog);
            popUp.setView(about)
                    .setTitle(getString(R.string.settingTitle))
                    .setPositiveButton(getString(R.string.save), (dialogInterface, i) -> {
                        server[0] = serverET.getText().toString();
                        settings.edit()
                                .putString("server", server[0])
                                .putString("apiKey", apiET.getText().toString())
                                .apply();

                        selectedMode = spinner.getSelectedItem().toString();
                        Toast.makeText(this, selectedMode, Toast.LENGTH_LONG).show();

                        //Retrieve languages into shared preferences
                        try {
                            retrieveLanguages();
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    })
                    .setNegativeButton(getString(R.string.close), null)
                    .show();
        });
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        // 如果正在请求权限，就保存一个标记
        if (isRequestingPermission) {
            outState.putBoolean(IS_REQUESTING_PERMISSION, true);
        }
    }

    private static final int REQUEST_CAMERA_PERMISSION = 200;

    private void startCameraIntentForResult() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestCameraPermission();
        } else {
            startCamera();
        }
    }

    private void requestCameraPermission() {
        isRequestingPermission = true;
        ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, REQUEST_CAMERA_PERMISSION);
    }

    private void startCamera() {
        // Clean up last time's image
        imageUri = null;

        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (takePictureIntent.resolveActivity(getPackageManager()) != null) {
            ContentValues values = new ContentValues();
            values.put(MediaStore.Images.Media.TITLE, "New Picture");
            values.put(MediaStore.Images.Media.DESCRIPTION, "From Camera");
            imageUri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
            takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, imageUri);
            startActivityForResult(takePictureIntent, REQUEST_IMAGE_CAPTURE);
        }
    }

    private void startChooseImageIntentForResult() {
        Intent intent = new Intent();
        intent.setType("image/*");
        intent.setAction(Intent.ACTION_GET_CONTENT);
        startActivityForResult(Intent.createChooser(intent, "Select Picture"), REQUEST_CHOOSE_IMAGE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (isRequestingPermission) {
            // 如果正在请求权限，就不进行初始化操作
            return;
        }
        if (requestCode == REQUEST_IMAGE_CAPTURE && resultCode == RESULT_OK) {
            Bitmap imageBitmap = null;
            try {
                imageBitmap = BitmapUtils.getBitmapFromContentUri(getContentResolver(), imageUri);
            } catch (IOException e) {
                e.printStackTrace();
            }
            crop(imageBitmap);
        } else if (requestCode == REQUEST_CHOOSE_IMAGE && resultCode == RESULT_OK) {
            // In this case, imageUri is returned by the chooser, save it.
            Bitmap imageBitmap = null;
            imageUri = data.getData();
            try {
                imageBitmap = BitmapUtils.getBitmapFromContentUri(getContentResolver(), imageUri);
            } catch (IOException e) {
                e.printStackTrace();
            }
            crop(imageBitmap);
        } else if (requestCode == REQUEST_CROP_IMAGE && resultCode == RESULT_OK) {
            imageUri = data.getData();
            tryReloadAndDetectInImage();
        } else {
            super.onActivityResult(requestCode, resultCode, data);
        }
    }

    //裁剪函数
    private void crop(Bitmap bitmap) {
        Intent intent = new Intent("com.android.camera.action.CROP");
        Uri uri = Uri.parse(MediaStore.Images.Media.insertImage(MainActivity.this.getContentResolver(), bitmap, "1", "1"));
        intent.setDataAndType(uri, "image/*");//设置要缩放的图片Uri和类型
        intent.putExtra("crop", "true");
        intent.putExtra("scale", true);//缩放
        intent.putExtra("return-data", false);//当为true的时候就返回缩略图，false就不返回，需要通过Uri
        intent.putExtra("noFaceDetection", false);//前置摄像头
        startActivityForResult(intent, REQUEST_CROP_IMAGE);//打开剪裁Activity
    }

    private void tryReloadAndDetectInImage() {
        Log.d(TAG, "Try reload and detect image");
        try {
            if (imageUri == null) {
                return;
            }
            Bitmap imageBitmap = BitmapUtils.getBitmapFromContentUri(getContentResolver(), imageUri);
            if (imageBitmap == null) {
                return;
            }
//            if (imageProcessor != null) {
//                Log.d(TAG, "Starting OCR...");
//                imageProcessor.processBitmap(imageBitmap);
//            } else {
//                Log.e(TAG, "Null imageProcessor, please check adb logs for imageProcessor creation error");
//            }
            try{
                getTextFromImage(imageBitmap);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        } catch (IOException e) {
            Log.e(TAG, "Error retrieving saved image");
            imageUri = null;
        }
    }

//    private void createImageProcessor() {
//        if (imageProcessor != null) {
//            imageProcessor.stop();
//        }
//
//        try {
//            switch (selectedMode) {
//                case TEXT_RECOGNITION_LATIN:
//                    imageProcessor =
//                            new TextRecognitionProcessor(this, new TextRecognizerOptions.Builder().build(), mhandler);
//                    break;
//                case TEXT_RECOGNITION_CHINESE:
//                    imageProcessor =
//                            new TextRecognitionProcessor(
//                                    this, new ChineseTextRecognizerOptions.Builder().build(), mhandler);
//                    break;
//                case TEXT_RECOGNITION_DEVANAGARI:
//                    imageProcessor =
//                            new TextRecognitionProcessor(
//                                    this, new DevanagariTextRecognizerOptions.Builder().build(), mhandler);
//                    break;
//                case TEXT_RECOGNITION_JAPANESE:
//                    imageProcessor =
//                            new TextRecognitionProcessor(
//                                    this, new JapaneseTextRecognizerOptions.Builder().build(), mhandler);
//                    break;
//                case TEXT_RECOGNITION_KOREAN:
//                    imageProcessor =
//                            new TextRecognitionProcessor(this, new KoreanTextRecognizerOptions.Builder().build(), mhandler);
//                    break;
//                default:
//                    Log.e(TAG, "Unknown selectedMode: " + selectedMode);
//            }
//        } catch (Exception e) {
//            Log.e(TAG, "Can not create image processor: " + selectedMode, e);
//            Toast.makeText(
//                            getApplicationContext(),
//                            "Can not create image processor: " + e.getMessage(),
//                            Toast.LENGTH_LONG)
//                    .show();
//        }
//    }

    @Override
    public void onResume() {
        super.onResume();
        Log.d(TAG, "onResume");
//        createImageProcessor();
        tryReloadAndDetectInImage();
    }

    @Override
    public void onPause() {
        super.onPause();
//        if (imageProcessor != null) {
//            imageProcessor.stop();
//        }
    }

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable updateRecordingTextRunnable = new Runnable() {
        private int dotCount = 0;

        @Override
        public void run() {
            dotCount = (dotCount % 3) + 1;
            String text = "录音中";
            for (int i = 0; i < dotCount; i++) {
                text += ".";
            }
//            binding.voice.setTextSize(11);
            binding.voice.setText(text);
            if (isStart) {
                handler.postDelayed(this, 500); // 每500毫秒更新一次
            }
        }
    };


    private static final int REQUEST_RECORD_AUDIO_PERMISSION = 201;

    private void startRecord() {
        // Check if the Record Audio permission is already available.
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            // Record Audio permission has not been granted.
            requestRecordAudioPermission();
        } else {
            // Record Audio permissions is already available, show the camera preview.
            startRecording();
        }
    }

    private void requestRecordAudioPermission() {
        // Record Audio permission has not been granted yet. Request it directly.
        isRequestingPermission = false;
        ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO}, REQUEST_RECORD_AUDIO_PERMISSION);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        isRequestingPermission = false;
        if (requestCode == REQUEST_RECORD_AUDIO_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permission has been granted. Start recording
                startRecording();
            } else {
                // Permission request was denied.
                if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.RECORD_AUDIO)) {
                    // Show an explanation to the user *asynchronously* -- don't block
                    // this thread waiting for the user's response! After the user
                    // sees the explanation, try again to request the permission.
                    new AlertDialog.Builder(this)
                            .setTitle("需要录音权限")
                            .setMessage("此应用需要录音权限以进行录音功能。请授予录音权限。")
                            .setPositiveButton("OK", (dialog, id) -> requestRecordAudioPermission())
                            .setNegativeButton("Cancel", (dialog, id) -> dialog.cancel())
                            .create()
                            .show();
                } else {
                    // User has chosen to deny the permission permanently,
                    // you can prompt the user here to open the settings and manually allow the permission
                    Toast.makeText(this, "录音权限被拒绝，无法录音。请在设置中手动开启。", Toast.LENGTH_LONG).show();
                }
            }
        }
        if (requestCode == REQUEST_CAMERA_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permission has been granted. Start camera preview Activity.
                startCamera();
            } else {
                // Permission request was denied.
                Toast.makeText(this, "Camera permission was denied", Toast.LENGTH_SHORT).show();
            }
        }
    }

    //开始录制
    private void startRecording() {
//        String audioFilePath = "";
        if (mr == null) {
            File dir = new File(getExternalFilesDir(null), "audio");
            if (!dir.exists()) {
                dir.mkdirs();
            }
            File soundFile = new File(dir, System.currentTimeMillis() + ".amr");
            if (!soundFile.exists()) {
                try {
                    soundFile.createNewFile();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

            mr = new MediaRecorder();
            mr.setAudioSource(MediaRecorder.AudioSource.MIC);
            mr.setOutputFormat(MediaRecorder.OutputFormat.AMR_WB);
            mr.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_WB);
            audioFile = soundFile.getAbsolutePath(); // 使用 soundFile 的路径

            mr.setOutputFile(audioFile);

            try {
                mr.prepare();
                mr.start();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
//        return audioFile;
    }

    private void stopRecord(){
        if (mr!=null){
            mr.stop();
            mr.reset();
            mr.release();
            mr = null;
        }
    }

    //将录制的音频传到后端，并返回所识别的文本
    private String requestAudioToText(String audioFilePath, String serverUrl) {
        String translatedText = "";
        try {
            // 读取音频文件并转换为 Base64 格式的字符串
            String audioBase64 = encodeAudioFileToBase64(audioFilePath);

            // 创建一个 JSON 对象，包含音频数据和其他参数
            JSONObject json = new JSONObject();
            json.put("base64", audioBase64);
            json.put("format", "wav");
            json.put("rate", 8000);
            json.put("channel", 1);
            json.put("token", "api_key");
            json.put("len", audioFile.length());
            json.put("source", sourceLangCode);
            json.put("target", targetLangCode);

            // 创建一个 HttpURLConnection 对象
            URL url = new URL(serverUrl+"/translate_audio2text");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(CONN_TIMEOUT);
            conn.setReadTimeout(READ_TIMEOUT);
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true);
            conn.setDoInput(true);

            // 发送请求数据
            OutputStream os = conn.getOutputStream();
            os.write(json.toString().getBytes(StandardCharsets.UTF_8));
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

            // 解析响应数据并获取 translatedText 字段的值
            JSONObject responseJson = new JSONObject(response.toString());
            translatedText = responseJson.getString("translatedText");
        } catch (Exception e) {
            // 处理异常
            e.printStackTrace();
        }

        return translatedText;
    }


    private byte[] readFileToByteArray(String filePath) throws IOException {
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

    public String encodeAudioFileToBase64(String filePath) throws IOException {
        byte[] audioData = readFileToByteArray(filePath);
        return Base64.encodeToString(audioData, Base64.DEFAULT);
    }

    private void getAudioText(String filePath) throws Exception {
        String server = settings.getString("server", DEFAULT_SERVER);

        Thread thread = new Thread(() -> {
            String error = "";
            String audioToText = "";
            try {
                audioToText = requestAudioToText(filePath,server);
            } catch (Exception e) {
                e.printStackTrace();
                error = e.toString();
            }

            Bundle bundle = new Bundle();
            bundle.putString("audioToText", audioToText);
            bundle.putString("serverError", error);
            Message msg = new Message();
            msg.setData(bundle);
            msg.what = AudioText;
            mhandler.sendMessage(msg);
        });

        thread.start();
    }


    private JSONObject requestAudioFromText(String server, String apiKey, String text) throws Exception{
        String audio = "";
        String type = "";
        JSONObject responseJson;
        URL url = new URL(server + "/translate_text2audio");
        Log.d("yimt", "Request audio from " + url);

        HttpURLConnection conn = null;
        try {
            conn = server.startsWith("https") ?
                    (HttpsURLConnection) url.openConnection() : (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(CONN_TIMEOUT);
            conn.setReadTimeout(READ_TIMEOUT);
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");

            JSONObject json = new JSONObject();
//            String q = binding.TranslatedTV.getText().toString().replace("&", "%26");
            String q = text.replace("&", "%26");
            String Source = sourceLangCode;
            String Target = targetLangCode;
//            String data = "text=" + q + "&token="+ "123";
            if (!apiKey.equals(""))
//                data += "&api_key=" + apiKey;
                json.put("api_key", apiKey);
            json.put("text", q);
            json.put("token", "123");
            json.put("source", Source);
            json.put("target", Target);
            OutputStream os = conn.getOutputStream();
            os.write(json.toString().getBytes(StandardCharsets.UTF_8));
            os.close();

            InputStream is = conn.getInputStream();
            BufferedReader reader = new BufferedReader(new InputStreamReader(is));
            String line;
            StringBuilder response = new StringBuilder();
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
            reader.close();
            is.close();
//            conn.disconnect();

            // 解析响应数据并获取 translatedText 字段的值
            responseJson = new JSONObject(response.toString());
            audio = responseJson.getString("base64");
            type = responseJson.getString("type");
        } finally {
            conn.disconnect();
        }

        return responseJson;
    }

    //接受服务器返回的音频数据并播放，接收的格式为base64
    private void getAudio(String text) {
        String server = settings.getString("server", DEFAULT_SERVER);

        Thread thread = new Thread(() -> {
            String error = "";
            JSONObject audioMsg = new JSONObject();
            try {
                audioMsg = requestAudioFromText(server, "", text);
            } catch (Exception e) {
                e.printStackTrace();
                error = e.toString();
            }

            Bundle bundle = new Bundle();
            try {
                bundle.putString("audio", audioMsg.getString("base64"));
                bundle.putString("type", audioMsg.getString("type"));
            } catch (JSONException e) {
                throw new RuntimeException(e);
            }
            bundle.putString("serverError", error);
            Message msg = new Message();
            msg.setData(bundle);
            msg.what = AudioPlay;
            mhandler.sendMessage(msg);
        });
        thread.start();
    }

    private void playAudio(String audio,String type){
        byte[] audioData = Base64.decode(audio, Base64.DEFAULT);
        try {
            // Create a temporary audio file
            File tempAudioFile = File.createTempFile("temp_audio", "."+type);

            // Write the audio data to the temporary file
            OutputStream os = new FileOutputStream(tempAudioFile);
            os.write(audioData);
            os.close();

            // Initialize and start the MediaPlayer
            mediaPlayer = new MediaPlayer();
            mediaPlayer.setDataSource(tempAudioFile.getAbsolutePath());
            mediaPlayer.prepare();
            mediaPlayer.start();

            // Add an event listener to release resources when playback is complete
            mediaPlayer.setOnCompletionListener(mp -> {
                mediaPlayer.release();
                tempAudioFile.delete(); // Delete the temporary file
            });
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private JSONObject requestTextFromImage(String server, String apiKey, Bitmap imageBitmap) throws Exception {
        String text = "";
        JSONObject responseJson;
        URL url = new URL(server + "/translate_image2text");
        Log.d("yimt", "Request text from " + url);

        HttpURLConnection conn = null;
        try {
            conn = server.startsWith("https") ?
                    (HttpsURLConnection) url.openConnection() : (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(CONN_TIMEOUT);
            conn.setReadTimeout(READ_TIMEOUT);
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");

            JSONObject json = new JSONObject();
            String base64 = encodeImageToBase64(imageBitmap);
            String Source = sourceLangCode;
            String Target = targetLangCode;
            json.put("base64", base64);
            json.put("source", Source);
            json.put("target", Target);
            if (!apiKey.equals(""))
                json.put("token", apiKey);
            OutputStream os = conn.getOutputStream();
            os.write(json.toString().getBytes(StandardCharsets.UTF_8));
            os.close();

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

            // 解析响应数据并获取 translatedText 字段的值
            responseJson = new JSONObject(response.toString());
            text = responseJson.getString("translatedText");
        } finally {
            conn.disconnect();
        }
//        return text;
        return responseJson;
    }

    private void getTextFromImage(Bitmap imageBitmap) throws Exception {
        String server = settings.getString("server", DEFAULT_SERVER);

        Thread thread = new Thread(() -> {
            String error = "";
            JSONObject result = new JSONObject();
            try {
                if (server != null) {
                    result = requestTextFromImage(server, "api_key", imageBitmap);
                }
            } catch (Exception e) {
                e.printStackTrace();
                error = e.toString();
            }

            Bundle bundle = new Bundle();
            try {
                bundle.putString("ocr_text", result.getString("originalText"));
                bundle.putString("translatedText", result.getString("translatedText"));
            } catch (JSONException e) {
                throw new RuntimeException(e);
            }
            bundle.putString("serverError", error);
            Message msg = new Message();
            msg.setData(bundle);
            msg.what = TextRecog;
            mhandler.sendMessage(msg);
        });

        thread.start();
    }

    private String encodeImageToBase64(Bitmap imageBitmap) throws IOException {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        imageBitmap.compress(Bitmap.CompressFormat.JPEG, 100, byteArrayOutputStream);
        byte[] imageBytes = byteArrayOutputStream.toByteArray();
        return Base64.encodeToString(imageBytes, Base64.DEFAULT);
    }

    private HashMap<String, String> getLanguageMap(String languages) {
        HashMap<String, String> langMap = new HashMap<String, String>();
        String[] str = languages.split(",");
        for (String s : str) {
            if (!s.equals("")) {
                String[] pair = s.split(":");
                langMap.put(pair[0], pair[1]);
            }
        }

        return langMap;
    }

    private String requestLanguages(String server) throws Exception {
        String languages = "";
        URL url = new URL(server + "/languages");

        Log.d("yimt", "Request languages from " + url);

        HttpURLConnection conn = null;
        try {
            conn = server.startsWith("https") ?
                    (HttpsURLConnection) url.openConnection() : (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(CONN_TIMEOUT);
            conn.setReadTimeout(READ_TIMEOUT);
            conn.setRequestMethod("GET");
            conn.setRequestProperty("accept", "application/json");

            InputStream inputStream = conn.getInputStream();
            BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
            JSONArray jsonArray = new JSONArray(reader.readLine());
            for (int i = 0; i < jsonArray.length(); i++) {
                String langCode = jsonArray.getJSONObject(i).getString("code");
                String langName = jsonArray.getJSONObject(i).getString("name");
                languages += langCode + ":" + langName + ",";
            }
        } finally {
            conn.disconnect();
        }

        if (languages.length() > 0)
            return languages.substring(0, languages.length() - 1);
        return languages;
    }

    private void retrieveLanguages() throws Exception {
        String server = settings.getString("server", DEFAULT_SERVER);

        Thread thread = new Thread(() -> {
            String languages = "";
            String error = "";
            try {
                languages = requestLanguages(server);
            } catch (Exception e) {
                e.printStackTrace();
                error = e.toString();
            }

            Bundle bundle = new Bundle();
            bundle.putString("languages", languages);
            bundle.putString("serverError", error);
            Message msg = new Message();
            msg.setData(bundle);
            msg.what = LANGUAGES;
            mhandler.sendMessage(msg);
        });

        thread.start();
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

            String q = binding.SourceText.getText().toString().replace("&", "%26");
            String source = sourceLangCode;
            String target = targetLangCode;
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
        String languages = settings.getString("languages", "");
        if (!(binding.SourceText.getText().toString().equals("") || languages.equals(""))) {//fix
            String server = settings.getString("server", DEFAULT_SERVER);
            String apiKey = settings.getString("apiKey", "");

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
                bundle.putString("transString", translation);
                bundle.putString("serverError", error);
                Message msg = new Message();
                msg.setData(bundle);
                msg.what = TRANSLATE;
                mhandler.sendMessage(msg);
            });

            thread.start();
        }
    }

    private void setSourceLang() {
        String languages = settings.getString("languages", "");
        if (!languages.equals("")) {
            HashMap<String, String> langMap = getLanguageMap(languages);
            String sourceLang = AUTO_LANG_NAME;
            if (langMap.containsKey(sourceLangCode))
                sourceLang = langMap.get(sourceLangCode);
//            binding.SourceLanguageTop.setText(sourceLang);
            binding.SourceLanguageBot.setText(sourceLang);
            settings.edit()
                    .putString("Source", sourceLangCode)
                    .apply();
            switch (sourceLangCode) {
                case "zh":
                    selectedMode = TEXT_RECOGNITION_CHINESE;
                    break;
                case "en":
                    selectedMode = TEXT_RECOGNITION_LATIN;
                    break;
                case "ko":
                    selectedMode = TEXT_RECOGNITION_KOREAN;
                    break;
                case "ja":
                    selectedMode = TEXT_RECOGNITION_JAPANESE;
                    break;
                case "sa":
                    selectedMode = TEXT_RECOGNITION_DEVANAGARI;
                    break;
            }
//            createImageProcessor();
        }
    }

    private void setTargetLang() {
        String languages = settings.getString("languages", "");
        if (!languages.equals("")) {
            HashMap<String, String> langMap = getLanguageMap(languages);
            String targetLang = langMap.get(targetLangCode);
//            binding.TargetLanguageTop.setText(targetLang);
            binding.TargetLanguageBot.setText(targetLang);
            settings.edit()
                    .putString("Target", targetLangCode)
                    .apply();
        }
    }

    private void chooseLang(Boolean source) {
        String languages = settings.getString("languages", "");
        ArrayList<String> langCodes = new ArrayList<String>();
        langCodes.add(AUTO_LANG_CODE);
        ArrayList<String> langNames = new ArrayList<String>();
        langNames.add(AUTO_LANG_NAME);
        if (!languages.equals("")) {
            HashMap<String, String> langMap = getLanguageMap(languages);
            for (Map.Entry<String, String> e : langMap.entrySet()) {
                langCodes.add(e.getKey());
                langNames.add(e.getValue());
            }
        }

        if (source) {
            new AlertDialog.Builder(
                    this, R.style.AlertDialog
            )
                    .setTitle(getString(R.string.chooseLang))
                    .setItems(langNames.toArray(new String[langNames.size()]), (dialog, which) -> {
                        String c = langCodes.get(which);
                        sourceLangCode = c;
                        setSourceLang();
                        translateText();
                    })
                    .setPositiveButton(getString(R.string.abort), (dialog, which) -> dialog.cancel())
                    .show();
        } else {
            langCodes.remove(0);
            langNames.remove(0);
            new AlertDialog.Builder(
                    this, R.style.AlertDialog
            )
                    .setTitle(getString(R.string.chooseLang))
                    .setItems(langNames.toArray(new String[langNames.size()]), (dialog, which) -> {
                        String c = langCodes.get(which);
                        targetLangCode = c;
                        setTargetLang();

                        translateText();
                    })
                    .setPositiveButton(getString(R.string.abort), (dialog, which) -> dialog.cancel())
                    .show();
        }
    }
}