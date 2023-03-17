package com.yimt;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.provider.MediaStore;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.MenuInflater;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.PopupMenu;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.snackbar.Snackbar;
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions;
import com.google.mlkit.vision.text.devanagari.DevanagariTextRecognizerOptions;
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions;
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;
import com.yimt.databinding.ActivityMainBinding;
import com.yimt.ocr.TextRecognitionProcessor;
import com.yimt.ocr.BitmapUtils;
import com.yimt.ocr.VisionImageProcessor;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.DataInputStream;
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
    private final static String DEFAULT_SERVER = "http://192.168.1.104:5555";
    private static final int REQUEST_IMAGE_CAPTURE = 1001;
    private static final int REQUEST_CHOOSE_IMAGE = 1002;
    private static final int REQUEST_CROP_IMAGE = 1003;
    private static final String TAG = "StillImageActivity";
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
    //add
    private Uri imageUri;
    private VisionImageProcessor imageProcessor;
    private String selectedMode = TEXT_RECOGNITION_CHINESE;

//    private static int REQ_Still = 1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        MainActivity activity = this;
        settings = getSharedPreferences("com.yimt", 0);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        createImageProcessor();
        findViewById(R.id.select_image_button)
                .setOnClickListener(
                        view -> {
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
                    String text = (String) data.get("translate_text");
                    binding.SourceText.setText(text);
                }
            }
        };

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
                imm.hideSoftInputFromWindow(view1.getWindowToken(), 0);
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

    private void startCameraIntentForResult() {
        // Clean up last time's image
        imageUri = null;
//        preview.setImageBitmap(null);

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
            if (imageProcessor != null) {
                imageProcessor.processBitmap(imageBitmap);
            } else {
                Log.e(TAG, "Null imageProcessor, please check adb logs for imageProcessor creation error");
            }
        } catch (IOException e) {
            Log.e(TAG, "Error retrieving saved image");
            imageUri = null;
        }
    }

    private void createImageProcessor() {
        if (imageProcessor != null) {
            imageProcessor.stop();
        }
        try {
            switch (selectedMode) {
                case TEXT_RECOGNITION_LATIN:
                    if (imageProcessor != null) {
                        imageProcessor.stop();
                    }
                    imageProcessor =
                            new TextRecognitionProcessor(this, new TextRecognizerOptions.Builder().build(), mhandler);
                    break;
                case TEXT_RECOGNITION_CHINESE:
                    if (imageProcessor != null) {
                        imageProcessor.stop();
                    }
                    imageProcessor =
                            new TextRecognitionProcessor(
                                    this, new ChineseTextRecognizerOptions.Builder().build(), mhandler);
                    break;
                case TEXT_RECOGNITION_DEVANAGARI:
                    if (imageProcessor != null) {
                        imageProcessor.stop();
                    }
                    imageProcessor =
                            new TextRecognitionProcessor(
                                    this, new DevanagariTextRecognizerOptions.Builder().build(), mhandler);
                    break;
                case TEXT_RECOGNITION_JAPANESE:
                    if (imageProcessor != null) {
                        imageProcessor.stop();
                    }
                    imageProcessor =
                            new TextRecognitionProcessor(
                                    this, new JapaneseTextRecognizerOptions.Builder().build(), mhandler);
                    break;
                case TEXT_RECOGNITION_KOREAN:
                    if (imageProcessor != null) {
                        imageProcessor.stop();
                    }
                    imageProcessor =
                            new TextRecognitionProcessor(this, new KoreanTextRecognizerOptions.Builder().build(), mhandler);
                    break;
                default:
                    Log.e(TAG, "Unknown selectedMode: " + selectedMode);
            }
        } catch (Exception e) {
            Log.e(TAG, "Can not create image processor: " + selectedMode, e);
            Toast.makeText(
                            getApplicationContext(),
                            "Can not create image processor: " + e.getMessage(),
                            Toast.LENGTH_LONG)
                    .show();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        Log.d(TAG, "onResume");
        createImageProcessor();
        tryReloadAndDetectInImage();
    }

    @Override
    public void onPause() {
        super.onPause();
        if (imageProcessor != null) {
            imageProcessor.stop();
        }
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
            binding.SourceLanguageTop.setText(sourceLang);
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
            createImageProcessor();
        }
    }

    private void setTargetLang() {
        String languages = settings.getString("languages", "");
        if (!languages.equals("")) {
            HashMap<String, String> langMap = getLanguageMap(languages);
            String targetLang = langMap.get(targetLangCode);
            binding.TargetLanguageTop.setText(targetLang);
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