package com.yimt;

import static com.yimt.ImageUtils.CODE_CROP_IMG;
import static com.yimt.ImageUtils.CODE_SETIMG_ALNUM;
import static com.yimt.ImageUtils.CODE_SETIMG_CAM;
import static com.yimt.Utils.encodeAudioFileToBase64;
import static com.yimt.Utils.encodeFileToBase64;
// import static com.yimt.Utils.lang2code;
import static com.yimt.Utils.parseLanguages;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Service;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.Vibrator;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import com.google.android.material.snackbar.Snackbar;
import com.yimt.databinding.ActivityMainBinding;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;


public class MainActivity extends AppCompatActivity {

    private ActivityMainBinding binding;

    private SharedPreferences settings;

    private static final int PERMISSION_REQ_CODE = 300;

    private boolean writePermission = false;
    private boolean cameraPermission = false;
    private boolean recordPermission = false;

    private final static String DEFAULT_SERVER = "http://192.168.1.104:5555";

    // 语言代码到名称
    private HashMap<String, String> langcode2Name = new HashMap<>();

    private ImageUtils imageUtils = new ImageUtils();

    private AudioUtils audioUtils = new AudioUtils();

    private String recordFile = null;

    @SuppressLint("ClickableViewAccessibility")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        settings = getSharedPreferences("com.yimt", 0);

        String langSettings = settings.getString("languages", "");
        if(langSettings.isEmpty())  // 无缓冲语言列表
            getLanguages();
        else
            setLanguages(langSettings);

        ArrayList<String> permissions = new ArrayList<String>();
        // 检查录音权限
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED)
            permissions.add(Manifest.permission.RECORD_AUDIO);

        // 检查写卡权限
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED)
            permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);

        // 检查拍照权限
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED)
            permissions.add(Manifest.permission.CAMERA);

        if (permissions.size() > 0)  // 有需要授权的
            ActivityCompat.requestPermissions(this, permissions.toArray(new String[0]), PERMISSION_REQ_CODE);

        // 设置按钮
        binding.Settings.setOnClickListener(view -> {
            View about = getLayoutInflater().inflate(R.layout.about, null);
            EditText serverET = about.findViewById(R.id.Server);
            EditText apiET = about.findViewById(R.id.Api);
            String server = settings.getString("server", DEFAULT_SERVER);
            String apiKey = settings.getString("apiKey", "");
            serverET.setText(server);
            apiET.setText(apiKey);
            AlertDialog.Builder popUp = new AlertDialog.Builder(this, R.style.AlertDialog);
            popUp.setView(about)
                    .setTitle(getString(R.string.settingTitle))
                    .setPositiveButton(getString(R.string.save), (dialogInterface, i) -> {
                        settings.edit()
                                .putString("server", serverET.getText().toString())
                                .putString("apiKey", apiET.getText().toString())
                                .apply();
                        getLanguages();
                    })
                    .setNegativeButton(getString(R.string.close), null)
                    .show();
        });

        // 翻译按钮
        binding.StartTranslation.setOnClickListener(view -> {
            String text = binding.textSource.getText().toString().trim();
            if (!text.isEmpty()) {
                translateText(text);

                binding.Pending.setVisibility(View.VISIBLE);  // 显示进度条

                binding.StartTranslation.setEnabled(false);
                binding.Camera.setEnabled(false);
                binding.Gallery.setEnabled(false);
                binding.ReadTranslation.setEnabled(false);
                binding.MicroPhone.setEnabled(false);
            }
            else
                Toast.makeText(MainActivity.this, "输入文本为空", Toast.LENGTH_SHORT).show();

        });

        // 话筒按钮: 长按录音
        binding.MicroPhone.setOnLongClickListener(v -> {
            // 录音前震动提示
            Vibrator vb = (Vibrator)getSystemService(Service.VIBRATOR_SERVICE);
            vb.vibrate(300);

            recordFile = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS).getAbsolutePath() +
                    "/" + System.currentTimeMillis() + ".pcm";

            // 通过录音线程录音
            audioUtils.startRecordAudio(recordFile);

            return true;
        });

        // 话筒按钮: 松开结束录音
        binding.MicroPhone.setOnTouchListener((view, motionEvent) -> {
            if(recordFile == null)
                return false;

            if (motionEvent.getAction() == MotionEvent.ACTION_UP) {
                audioUtils.stopRecordAudio();

                // Toast.makeText(MainActivity.this, "录音完成", Toast.LENGTH_LONG).show();
                Log.i("YIMT", "录音完成");

                String wavFilePath = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS).getAbsolutePath()
                        + "/wav_" + System.currentTimeMillis() + ".wav";

                Log.i("YIMT", "WAV文件路径: " + wavFilePath);

                // 将PCM转换成WAV
                PcmToWavUtil ptwUtil = new PcmToWavUtil();
//                ptwUtil.pcmToWav(audioUtils.audioCacheFilePath, wavFilePath, true);
                ptwUtil.pcmToWav(recordFile, wavFilePath, true);

                // 异步ASR
                ASR(wavFilePath);

                binding.Pending.setVisibility(View.VISIBLE);  // 显示进度条

                binding.StartTranslation.setEnabled(false);
                binding.Camera.setEnabled(false);
                binding.Gallery.setEnabled(false);
                binding.ReadTranslation.setEnabled(false);
                binding.MicroPhone.setEnabled(false);

                recordFile = null;

                return true;
            }

            return false;
        });

        // 相机按钮
        binding.Camera.setOnClickListener(view -> {
            String sourceLang = getSourceLang();
            if(sourceLang.equals("auto")){
                Toast.makeText(MainActivity.this, "请选择源语言", Toast.LENGTH_LONG).show();
                return;
            }

            imageUtils.gotoCam(this);
        });

        // 相册按钮
        binding.Gallery.setOnClickListener(view -> {
            String sourceLang = getSourceLang();
            if(sourceLang.equals("auto")){
                Toast.makeText(MainActivity.this, "请选择源语言", Toast.LENGTH_LONG).show();
                return;
            }

            imageUtils.gotoAlbum(this);
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
            else {
                Toast.makeText(this, "输入内容为空", Toast.LENGTH_SHORT).show();
            }

        });

        // 分享按钮
        binding.Share.setOnClickListener(view -> {
            String translation = binding.textTarget.getText().toString();
            if (!translation.isEmpty()) {
                Intent shareIntent = new Intent(Intent.ACTION_SEND);
                shareIntent.setType("text/plain");
                shareIntent.putExtra(Intent.EXTRA_TEXT, translation);
                startActivity(Intent.createChooser(shareIntent, "分享翻译"));
            }
            else {
                Toast.makeText(this, "翻译内容为空", Toast.LENGTH_SHORT).show();
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
            else {
                Toast.makeText(this, "翻译内容为空", Toast.LENGTH_SHORT).show();
            }
        });

        // 播放按钮
        binding.ReadTranslation.setOnClickListener(v -> {
            String text = binding.textTarget.getText().toString();
            if (text.isEmpty()) {
                Toast.makeText(this, "翻译内容为空", Toast.LENGTH_SHORT).show();
                return;
            }

            // TTS
            TTS(text);

            binding.Pending.setVisibility(View.VISIBLE);  // 显示进度条

            binding.StartTranslation.setEnabled(false);
            binding.Camera.setEnabled(false);
            binding.Gallery.setEnabled(false);
            binding.ReadTranslation.setEnabled(false);
            binding.MicroPhone.setEnabled(false);
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK && requestCode == CODE_SETIMG_ALNUM) {  //相册数据返回
            imageUtils.cropImg(this, false, data);  //裁剪
        } else if (resultCode == RESULT_OK && requestCode == CODE_SETIMG_CAM) { //相机拍照返回
            imageUtils.cropImg(this, true, null);  //裁剪
        } else if (resultCode == RESULT_OK && requestCode == CODE_CROP_IMG) {  //裁剪图片返回
            String imgFilePath = imageUtils.cropImgFile.getPath();
            String sourceLang = getSourceLang();

            // OCR
            OCR(imgFilePath, sourceLang);

            binding.Pending.setVisibility(View.VISIBLE);  // 显示进度条

            binding.StartTranslation.setEnabled(false);
            binding.Camera.setEnabled(false);
            binding.Gallery.setEnabled(false);
            binding.ReadTranslation.setEnabled(false);
            binding.MicroPhone.setEnabled(false);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if(requestCode == PERMISSION_REQ_CODE && grantResults.length > 0){
            for(int i=0; i<grantResults.length; i++){
                if(permissions[i] == Manifest.permission.WRITE_EXTERNAL_STORAGE){

                }
            }
        }
    }

    // 翻译文本
    private void translateText(String text) {
        String server = settings.getString("server", DEFAULT_SERVER);
        String apiKey = settings.getString("apiKey", "");

        Thread thread = new Thread(() -> {
            try {
                final String translation = requestTranslate(server, apiKey, text);

                runOnUiThread(()->{
                    binding.Pending.setVisibility(View.GONE);  // 停止显示进度条

                    binding.textTarget.setText(translation);

                    binding.StartTranslation.setEnabled(true);
                    binding.Camera.setEnabled(true);
                    binding.Gallery.setEnabled(true);
                    binding.ReadTranslation.setEnabled(true);
                    binding.MicroPhone.setEnabled(true);
                });
            } catch (Exception e) {
                e.printStackTrace();

                final String error = e.toString();
                runOnUiThread(()->{
                    binding.Pending.setVisibility(View.GONE);  // 停止显示进度条

                    binding.StartTranslation.setEnabled(true);
                    binding.Camera.setEnabled(true);
                    binding.Gallery.setEnabled(true);
                    binding.ReadTranslation.setEnabled(true);
                    binding.MicroPhone.setEnabled(true);

                    binding.textTarget.setText("");

                    Toast.makeText(MainActivity.this, "机器翻译失败: " + error, Toast.LENGTH_LONG).show();
                });
            }
        });

        thread.start();
    }

    // 请求服务器进行文本翻译
    private String requestTranslate(String server, String apiKey, String text) throws Exception {
        String url = server + "/translate";
        Log.d("yimt", "Translate using " + url);

        JSONObject json = new JSONObject();
        if (!apiKey.equals(""))
            json.put("api_key", apiKey);
        String q = text.replace("&", "%26");
        String source = lang2code(binding.spinnerSrcLang.getSelectedItem().toString());
        String target = lang2code(binding.spinnerTgtLang.getSelectedItem().toString());
        json.put("q", q);
        json.put("source", source);
        json.put("target", target);

        JSONObject responseJson = Utils.requestService(url, json.toString());

        return responseJson.getString("translatedText");
    }

    private void TTS(String text) {
        String server = settings.getString("server", DEFAULT_SERVER);
        String apiKey = settings.getString("apiKey", "");

        Thread thread = new Thread(() -> {
            //String error = "";
            //JSONObject audioMsg = new JSONObject();
            try {
                JSONObject audioMsg = requestTTS(server, apiKey, text);

                runOnUiThread(()->{
                    try {
                        String audio = audioMsg.getString("base64");
                        String type = audioMsg.getString("type");

                        AudioUtils.playAudio(audio, type);
                    } catch (Exception e) {
                        Toast.makeText(MainActivity.this, "声音播放失败", Toast.LENGTH_LONG).show();
                        e.printStackTrace();
                    }

                    binding.Pending.setVisibility(View.GONE);
                    binding.StartTranslation.setEnabled(true);
                    binding.Camera.setEnabled(true);
                    binding.Gallery.setEnabled(true);
                    binding.ReadTranslation.setEnabled(true);
                    binding.MicroPhone.setEnabled(true);
                });
            } catch (Exception e) {
                e.printStackTrace();
                final String error = e.toString();

                runOnUiThread(()->{
                    binding.Pending.setVisibility(View.GONE);  //  停止显示进度条
                    binding.StartTranslation.setEnabled(true);
                    binding.Camera.setEnabled(true);
                    binding.Gallery.setEnabled(true);
                    binding.ReadTranslation.setEnabled(true);
                    binding.MicroPhone.setEnabled(true);

                    Toast.makeText(MainActivity.this, "语音合成失败: " + error, Toast.LENGTH_LONG).show();
                });
            }
        });
        thread.start();
    }

    private JSONObject requestTTS(String server, String apiKey, String text) throws Exception {
        String url = server + "/tts";

        JSONObject json = new JSONObject();
        if (!apiKey.isEmpty())
            json.put("api_key", apiKey);
        json.put("text", text);
        json.put("token", "123");
        String lang = lang2code(binding.spinnerTgtLang.getSelectedItem().toString());
        json.put("lang", lang);

        JSONObject responseJson = Utils.requestService(url, json.toString());

        return responseJson;
    }

    // ASR
    private void ASR(String filePath){
        String server = settings.getString("server", DEFAULT_SERVER);
        String apiKey = settings.getString("apiKey", "");

        Thread thread = new Thread(() -> {
            try {
                // 请求ASR服务
                final String audioToText = requestAudioToText(server, filePath);

                // 显示识别文本
                runOnUiThread(()->{
                    binding.Pending.setVisibility(View.GONE);  //  停止显示进度条

                    binding.textSource.setText((audioToText));
                    binding.textTarget.setText("");

                    binding.StartTranslation.setEnabled(true);
                    binding.Camera.setEnabled(true);
                    binding.Gallery.setEnabled(true);
                    binding.ReadTranslation.setEnabled(true);
                    binding.MicroPhone.setEnabled(true);});
            } catch (Exception e) {
                e.printStackTrace();
                final String error = e.toString();

                runOnUiThread(()->{
                    binding.Pending.setVisibility(View.GONE);  //  停止显示进度条

                    binding.textTarget.setText("");

                    binding.StartTranslation.setEnabled(true);
                    binding.Camera.setEnabled(true);
                    binding.Gallery.setEnabled(true);
                    binding.ReadTranslation.setEnabled(true);
                    binding.MicroPhone.setEnabled(true);

                    Toast.makeText(MainActivity.this, "语音识别失败: " + error, Toast.LENGTH_LONG).show();
                });
            }
        });

        thread.start();
    }

    // 请求ASR服务
    private String requestAudioToText(String server, String audioFilePath) throws IOException, JSONException {
        // 文件内容BASE64编码
        String audioBase64 = encodeAudioFileToBase64(audioFilePath);

        JSONObject json = new JSONObject();
        json.put("base64", audioBase64);

        json.put("format", "WAV");
        json.put("rate", 16000);
        json.put("channel", 1);
        json.put("token", "api_key");
        String lang = getSourceLang();  // lang2code(binding.spinnerSrcLang.getSelectedItem().toString());
        json.put("lang", lang);

        String url = server + "/asr";
        JSONObject responseJson = Utils.requestService(url, json.toString());

        return responseJson.getString("text");
    }

    private void OCR(String filePath, String sourceLang){
        Log.d("yimt", "getTextForImage");

        String server = settings.getString("server", DEFAULT_SERVER);
        String apiKey = settings.getString("apiKey", "");

        Thread thread = new Thread(() -> {
            try {
                final String text = requestTextForImage(server, apiKey, sourceLang, filePath);

                runOnUiThread(()->{
                    binding.Pending.setVisibility(View.GONE);  //  停止显示进度条
                    binding.textSource.setText(text);
                    binding.textTarget.setText("");

                    binding.StartTranslation.setEnabled(true);
                    binding.Camera.setEnabled(true);
                    binding.Gallery.setEnabled(true);
                    binding.ReadTranslation.setEnabled(true);
                    binding.MicroPhone.setEnabled(true);
                });
            } catch (Exception e) {
                e.printStackTrace();
                final String error = e.toString();

                runOnUiThread(()->{
                    binding.Pending.setVisibility(View.GONE);  //  停止显示进度条
                    binding.textTarget.setText("");

                    binding.StartTranslation.setEnabled(true);
                    binding.Camera.setEnabled(true);
                    binding.Gallery.setEnabled(true);
                    binding.ReadTranslation.setEnabled(true);
                    binding.MicroPhone.setEnabled(true);

                    Toast.makeText(MainActivity.this, "文字识别失败: " + error, Toast.LENGTH_LONG).show();
                });
            }
        });

        thread.start();
    }

    // 请求OCR服务
    private String requestTextForImage(String server, String apiKey, String sourceLang, String filePath) throws IOException, JSONException {
        String url = server + "/ocr";

        JSONObject json = new JSONObject();
        String base64 = encodeFileToBase64(filePath);
        json.put("base64", base64);
        json.put("lang", sourceLang);
        if (!apiKey.isEmpty())
            json.put("token", apiKey);

        JSONObject responseJson = Utils.requestService(url, json.toString());

        String text = responseJson.getString("text");

        return text;
    }

    private void getLanguages(){
        Log.d("yimt", "getLanguages");

        String server = settings.getString("server", DEFAULT_SERVER);
        String apiKey = settings.getString("apiKey", "");

        Thread thread = new Thread(() -> {
            try {
                final String languages = requestLanguages(server);
                settings.edit().putString("languages", languages).apply();

                runOnUiThread(()->setLanguages(languages));
            } catch (Exception e) {
                e.printStackTrace();

                runOnUiThread(()->Toast.makeText(MainActivity.this, "翻译语言列表获取失败", Toast.LENGTH_LONG).show());
            }
        });

        thread.start();
    }

    // 请求服务器获得语言列表
    private String requestLanguages(String server) throws IOException, JSONException {
        String url = server + "/languages";

        JSONArray jsonArray = new JSONArray(Utils.requestService(url, null, "GET"));

        String languages = "";
        for (int i = 0; i < jsonArray.length(); i++) {
            JSONObject jsonObject = jsonArray.getJSONObject(i);
            languages += jsonObject.getString("code") + "=" + jsonObject.getString("cname");

            if(i < jsonArray.length() - 1)
                languages += ",";
        }

        return languages;
    }

    // 获得语言语言代码
    private String getSourceLang(){
        String sl = binding.spinnerSrcLang.getSelectedItem().toString();

        return lang2code(sl);
    }

    // 设置语言下拉列表
    private void setLanguages(String langSettings){
        langcode2Name = parseLanguages(langSettings);
        String[] langNames = langcode2Name.values().toArray(new String[0]);

        // 添加源语言下拉语言列表
        ArrayAdapter<String> srcLangAdapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_dropdown_item, langNames);
        srcLangAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        binding.spinnerSrcLang.setAdapter(srcLangAdapter);
        binding.spinnerSrcLang.setSelection(0);

        // 添加目标下拉语言列表
        HashMap<String, String> noAutoLangs = (HashMap<String, String>) langcode2Name.clone();
        noAutoLangs.remove("auto");
        String[] tgtLangNames = noAutoLangs.values().toArray(new String[0]);
        ArrayAdapter<String> tgtLangAdapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_dropdown_item, tgtLangNames);
        tgtLangAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        binding.spinnerTgtLang.setAdapter(tgtLangAdapter);
        binding.spinnerTgtLang.setSelection(0);
    }

    // 从语言名称到代码
    private String lang2code(String lang) {
        for (Map.Entry<String, String> e : langcode2Name.entrySet()) {
            if(e.getValue().equals(lang))
                return e.getKey();
        }

        return null;
    }

}