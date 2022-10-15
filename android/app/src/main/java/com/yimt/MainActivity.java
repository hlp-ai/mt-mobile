package com.yimt;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import com.google.android.material.snackbar.Snackbar;
import androidx.appcompat.app.AppCompatActivity;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import com.yimt.databinding.ActivityMainBinding;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.Toast;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.net.HttpURLConnection;
import javax.net.ssl.HttpsURLConnection;


public class MainActivity extends AppCompatActivity {
    private SharedPreferences settings;
    private ActivityMainBinding binding;
    private Handler mhandler;

    final static int CONN_TIMEOUT = 15000;
    final static int READ_TIMEOUT = 15000;

    final static int LANGUAGES = 1;
    final static int TRANSLATE = 2;

    private final String AUTO_LANG_CODE = "auto";
    private final String AUTO_LANG_NAME = "AutoDetect";

    private String sourceLangCode = AUTO_LANG_CODE;
    private String targetLangCode = "zh";

    private final static String DEFAULT_SERVER = "http://192.168.1.104:5555";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        MainActivity activity = this;
        settings = getSharedPreferences("com.yimt", 0);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

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

//        new Handler(Looper.getMainLooper());
        mhandler = new Handler(Looper.getMainLooper()) {
            public void handleMessage(Message msg) {
                super.handleMessage(msg);

                if (msg.what == TRANSLATE) {
                    Bundle lc = msg.getData();
                    String transString = lc.getString("transString");
                    String serverError = lc.getString("serverError");
                    if (transString.isEmpty() && serverError.length()>0)
                        Toast.makeText(activity, serverError, Toast.LENGTH_LONG).show();
                    binding.TranslatedTV.setText(transString);
                    binding.translationPending.setVisibility(View.GONE);
                } else if (msg.what == LANGUAGES) {
                    Bundle lc = msg.getData();
                    String languages = lc.getString("languages");
                    String serverError = lc.getString("serverError");
                    if (languages.isEmpty() && serverError.length()>0) {
                        Toast.makeText(activity, serverError, Toast.LENGTH_LONG).show();
                    } else {
                        //Setting languages needs to happen before setSourceLang and setTargetLang
                        settings.edit()
                                .putString("languages", languages)
                                .apply();

                        setSourceLang();
                        setTargetLang();
                    }
                }
            }
        };

        binding.SourceText.addTextChangedListener(new TextWatcher() {
            private static final int DELAY_MILLIS = 5000;

            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {
            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
            }

            final Handler handler = new Handler(Looper.getMainLooper());
            final Runnable workRunnable = () -> translateText();

            @Override
            public void afterTextChanged(Editable editable) {
                handler.removeCallbacks(workRunnable);
                handler.postDelayed(workRunnable, DELAY_MILLIS);
                binding.translationPending.setVisibility(View.VISIBLE);
                if (editable.toString().equals(""))
                    binding.translationPending.setVisibility(View.GONE);
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
                    .setTitle(getString(R.string.about_title))
                    .setPositiveButton("Save", (dialogInterface, i) -> {
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

    private HashMap<String, String> getLanguageMap(String languages){
        HashMap<String, String> langMap = new HashMap<String, String>();
        String[] str = languages.split(",");
        for (String s : str) {
            if (!s.equals("")){
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
            return languages.substring(0, languages.length()-1);
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
        langCodes.add("auto");
        if (!languages.equals("")) {
            HashMap<String, String> langMap = getLanguageMap(languages);
            for (String c: langMap.keySet()){
                langCodes.add(c);
            }
        }

        if (source) {
            new AlertDialog.Builder(
                    this, R.style.AlertDialog
            )
                    .setTitle(getString(R.string.chooseLang))
                    .setItems(langCodes.toArray(new String[langCodes.size()]), (dialog, which) -> {
                        String c = langCodes.get(which);
                        sourceLangCode = c;
                        setSourceLang();

                        translateText();
                    })
                    .setPositiveButton(getString(R.string.abort), (dialog, which) -> dialog.cancel())
                    .show();
        }
        else {
            langCodes.remove(0);
            new AlertDialog.Builder(
                    this, R.style.AlertDialog
            )
                    .setTitle(getString(R.string.chooseLang))
                    .setItems(langCodes.toArray(new String[langCodes.size()]), (dialog, which) -> {
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