package com.yimt;

import static com.yimt.ImageUtils.CODE_CROP_IMG;
import static com.yimt.ImageUtils.CODE_SETIMG_ALNUM;
import static com.yimt.ImageUtils.CODE_SETIMG_CAM;
import static com.yimt.Utils.encodeAudioFileToBase64;
import static com.yimt.Utils.encodeFileToBase64;
import static com.yimt.Utils.lang2code;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import com.google.android.material.snackbar.Snackbar;
import com.yimt.databinding.ActivityMainBinding;

import org.json.JSONException;
import org.json.JSONObject;
import java.io.IOException;


public class MainActivity extends AppCompatActivity {

    private ActivityMainBinding binding;

    private SharedPreferences settings;

    private Handler mhandler;
    private static final int TRANSLATE_MSG = 201;
    private static final int READ_TEXT_MSG = 202;
    private static final int ASR_MSG = 203;
    private static final int OCR_MSG = 204;

    private final static String DEFAULT_SERVER = "http://192.168.1.104:5555";

    private final String[] languages = new String[]{"自动检测", "中文", "英文", "日文", "阿拉伯文"};

    private static final int REQUEST_CHOOSE_IMAGE = 101;
    private static final int REQUEST_CROP_IMAGE = 102;
    private static final int REQUEST_IMAGE_CAPTURE = 103;
    private static final int REQUEST_WRITE_STORAGE = 104;

    private static final int REQUEST_CAMERA_PERMISSION = 200;

    private ImageUtils imageUtils = new ImageUtils();

    private AudioUtils audioUtils = new AudioUtils();

    @SuppressLint("ClickableViewAccessibility")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        settings = getSharedPreferences("com.yimt", 0);

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
                    binding.Pending.setVisibility(View.GONE);  // 停止显示进度条

                    binding.StartTranslation.setEnabled(true);
                    binding.Camera.setEnabled(true);
                    binding.Gallery.setEnabled(true);
                    binding.ReadTranslation.setEnabled(true);
                    binding.MicroPhone.setEnabled(true);
                } else if (msg.what == READ_TEXT_MSG) {
                    Bundle data = msg.getData();
                    String serverError = data.getString("error");
                    binding.Pending.setVisibility(View.GONE);  //  停止显示进度条
                    if (serverError.length() > 0)
                        Toast.makeText(MainActivity.this, serverError, Toast.LENGTH_LONG).show();
                    else{
                        String audio = (String) data.get("audio");
                        String type = (String) data.get("type");
                        // playAudio(audio, type);
                        try {
                            audioUtils.playAudio(audio, type);
                        } catch (IOException e) {
                            Toast.makeText(MainActivity.this, "声音播放失败", Toast.LENGTH_LONG).show();
                            e.printStackTrace();
                        }
                    }

                    binding.StartTranslation.setEnabled(true);
                    binding.Camera.setEnabled(true);
                    binding.Gallery.setEnabled(true);
                    binding.ReadTranslation.setEnabled(true);
                    binding.MicroPhone.setEnabled(true);
                } else if (msg.what == ASR_MSG){
                    Bundle data = msg.getData();
                    String serverError = data.getString("error");
                    binding.Pending.setVisibility(View.GONE);  //  停止显示进度条
                    if (serverError.length() > 0)
                        Toast.makeText(MainActivity.this, serverError, Toast.LENGTH_LONG).show();
                    else{
                        String text = (String) data.get("audioToText");
                        binding.textSource.setText((text));
                    }

                } else if (msg.what == OCR_MSG) {
                    Bundle data = msg.getData();
                    String serverError = data.getString("error");
                    binding.Pending.setVisibility(View.GONE);  //  停止显示进度条
                    if (serverError.length() > 0)
                        Toast.makeText(MainActivity.this, serverError, Toast.LENGTH_LONG).show();
                    else{
                        String text = (String) data.get("text");
                        binding.textSource.setText(text);
                    }
                }
            }
        };

        // 添加下拉语言列表
        ArrayAdapter<String> srcLangAdapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_dropdown_item, languages);
        srcLangAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        binding.spinnerSrcLang.setAdapter(srcLangAdapter);

        // 添加下拉语言列表
        ArrayAdapter<String> tgtLangAdapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_dropdown_item, languages);
        tgtLangAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        binding.spinnerTgtLang.setAdapter(tgtLangAdapter);
        binding.spinnerTgtLang.setSelection(1);

        // 申请录音权限
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED)
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO}, 200);

        // 申请写卡权限
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED)
            // 请求权限
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    210);

        // 申请拍照权限
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED)
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.CAMERA},
                    REQUEST_CAMERA_PERMISSION);

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
            // Toast.makeText(TextActivity.this, "长按", Toast.LENGTH_LONG).show();
            try {
                // startRecording();
                audioUtils.startRecording(this);
            } catch (IOException e) {
                e.printStackTrace();
            }

            return true;
        });

        // 话筒按钮: 松开结束
        binding.MicroPhone.setOnTouchListener((view, motionEvent) -> {
            if (motionEvent.getAction() == MotionEvent.ACTION_UP) {
                // stopRecording();
                audioUtils.stopRecording();
                Toast.makeText(MainActivity.this, "录音完成", Toast.LENGTH_LONG).show();

                // getTextForAudio(audioFile);
                getTextForAudio(audioUtils.audioFile);

                binding.Pending.setVisibility(View.VISIBLE);  // 显示进度条

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
            String text = binding.textTarget.getText().toString();
            if (text.isEmpty()) {
                Toast.makeText(this, "没有可播放的文本", Toast.LENGTH_LONG).show();
                return;
            }

            readTranslation(text);
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
        if (resultCode == RESULT_OK && requestCode == CODE_SETIMG_ALNUM) {//相册数据返回
            imageUtils.cropImg(this, false, data, "image_hd");//裁剪
        } else if (resultCode == RESULT_OK && requestCode == CODE_SETIMG_CAM) {//相机拍照返回
            imageUtils.cropImg(this, true, null, "image_hd");//裁剪
            //imageUtils.refreshAlbum(mContext, imageUtils.camImgFile.getPath());//刷新相册
        } else if (resultCode == RESULT_OK && requestCode == CODE_CROP_IMG) {//裁剪图片返回
            String imgFilePath = imageUtils.cropImgFile.getPath();

            Toast.makeText(this, imgFilePath, Toast.LENGTH_LONG).show();

            String sourceLang = getSourceLang();
            getTextForImage(imgFilePath, sourceLang);

            binding.Pending.setVisibility(View.VISIBLE);  // 显示进度条
        }
    }

    private void translateText(String text) {
        String server = settings.getString("server", DEFAULT_SERVER);
        String apiKey = settings.getString("apiKey", "");

        Thread thread = new Thread(() -> {
            String translation = "";
            String error = "";
            try {
                translation = requestTranslate(server, apiKey, text);
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

    private String requestTranslate(String server, String apiKey, String text) throws Exception {
        String url = server + "/translate";
        Log.d("yimt", "Translate using " + url);

        JSONObject json = new JSONObject();
        if (!apiKey.equals(""))
            json.put("api_key", apiKey);
        String q = text.replace("&", "%26");
        String source = Utils.lang2code(binding.spinnerSrcLang.getSelectedItem().toString());
        String target = Utils.lang2code(binding.spinnerTgtLang.getSelectedItem().toString());
        json.put("q", q);
        json.put("source", source);
        json.put("target", target);

        JSONObject responseJson = Utils.requestService(url, json.toString());

        return responseJson.getString("translatedText");
    }

    private void readTranslation(String text) {
        String server = settings.getString("server", DEFAULT_SERVER);
        String apiKey = settings.getString("apiKey", "");

        Thread thread = new Thread(() -> {
            String error = "";
            JSONObject audioMsg = new JSONObject();
            try {
                audioMsg = requestTTS(server, apiKey, text);
            } catch (Exception e) {
                e.printStackTrace();
                error = e.toString();
            }

            Bundle bundle = new Bundle();
            bundle.putString("error", error);
            if(error.isEmpty()){
                try {
                    bundle.putString("audio", audioMsg.getString("base64"));
                    bundle.putString("type", audioMsg.getString("type"));
                } catch (JSONException e) {
                    throw new RuntimeException(e);
                }
            }
            Message msg = new Message();
            msg.setData(bundle);
            msg.what = READ_TEXT_MSG;
            mhandler.sendMessage(msg);
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
        String lang = Utils.lang2code(binding.spinnerTgtLang.getSelectedItem().toString());
        json.put("lang", lang);

        JSONObject responseJson = Utils.requestService(url, json.toString());

        return responseJson;
    }

    private void getTextForAudio(String filePath){
        String server = settings.getString("server", DEFAULT_SERVER);
        String apiKey = settings.getString("apiKey", "");

        Thread thread = new Thread(() -> {
            String error = "";
            String audioToText = "";
            try {
                audioToText = requestAudioToText(server, filePath);
            } catch (Exception e) {
                e.printStackTrace();
                error = e.toString();
            }

            Bundle bundle = new Bundle();
            bundle.putString("error", error);
            if(error.isEmpty())
                bundle.putString("audioToText", audioToText);
            Message msg = new Message();
            msg.setData(bundle);
            msg.what = ASR_MSG;
            mhandler.sendMessage(msg);
        });

        thread.start();
    }

    private String requestAudioToText(String server, String audioFilePath) throws IOException, JSONException {
        String audioBase64 = encodeAudioFileToBase64(audioFilePath);
        JSONObject json = new JSONObject();
        json.put("base64", audioBase64);
        json.put("format", "amr");
        json.put("rate", 8000);
        json.put("channel", 1);
        json.put("token", "api_key");
        // json.put("len", audioFile.length());
        json.put("len", audioUtils.audioFile.length());
//        json.put("source", "en");
//        json.put("target", "zh");
        String lang = Utils.lang2code(binding.spinnerSrcLang.getSelectedItem().toString());
        json.put("lang", lang);

        String url = server + "/asr";

        JSONObject responseJson = Utils.requestService(url, json.toString());

        return responseJson.getString("text");
    }

    private void getTextForImage(String filePath, String sourceLang){
        Log.d("yimt", "getTextForImage");

        String server = settings.getString("server", DEFAULT_SERVER);
        String apiKey = settings.getString("apiKey", "");

        String finalFilePath = filePath;
        Thread thread = new Thread(() -> {
            String error = "";
            String text = "";
            try {
                if (server != null) {
                    text = requestTextForImage(server, apiKey, sourceLang, finalFilePath);
                }
            } catch (Exception e) {
                e.printStackTrace();
                error = e.toString();
            }

            Bundle bundle = new Bundle();
            bundle.putString("error", error);
            if(error.isEmpty())
                bundle.putString("text", text);
            Message msg = new Message();
            msg.setData(bundle);
            msg.what = OCR_MSG;
            mhandler.sendMessage(msg);
        });

        thread.start();
    }

    private String requestTextForImage(String server, String apiKey, String sourceLang, String filePath) throws IOException, JSONException {
        String url = server + "/ocr";

        JSONObject json = new JSONObject();
        String base64 = encodeFileToBase64(filePath);
//        String Source = "en";
//        String Target = "zh";
        json.put("base64", base64);
//        json.put("source", Source);
//        json.put("target", Target);
        json.put("lang", sourceLang);
        if (!apiKey.isEmpty())
            json.put("token", apiKey);

        JSONObject responseJson = Utils.requestService(url, json.toString());

        String text = responseJson.getString("text");

        return text;
    }

    private String getSourceLang(){
        String sl = binding.spinnerSrcLang.getSelectedItem().toString();

        return lang2code(sl);
    }

    private String getTargetLang(){
        String tl = binding.spinnerTgtLang.getSelectedItem().toString();

        return lang2code(tl);
    }

}