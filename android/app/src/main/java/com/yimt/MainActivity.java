package com.yimt;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;

import androidx.appcompat.app.AppCompatActivity;

import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;

import com.yimt.databinding.ActivityMainBinding;

import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.net.HttpURLConnection;

import javax.net.ssl.HttpsURLConnection;


public class MainActivity extends AppCompatActivity {
    private SharedPreferences settings;
    private ActivityMainBinding binding;
    private int sourceLangId = 0;
    private int targetLangId = 0;
    private Handler mhandler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        MainActivity activity = this;
        settings = getSharedPreferences("com.yimt", 0);
        setTheme(R.style.LightTheme);
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        try {
            retrieveLanguages();
        } catch (Exception e) {
            e.printStackTrace();
        }

        String languages = settings.getString("languages", "");
        if (!languages.equals("")) {
            sourceLangId = settings.getInt("Source", 0);
            setSourceLang();
            targetLangId = settings.getInt("Target", 3);
            setTargetLang();
        }

        Intent intent = getIntent();
        if (intent.getAction().equals(Intent.ACTION_SEND)) {
            if (intent.getExtras() != null) {
                String s = getString(Integer.parseInt(Intent.EXTRA_TEXT));
                binding.SourceText.setText(s);
            }
            translateText();
        }

        new Handler(Looper.getMainLooper());
        mhandler = new Handler(Looper.getMainLooper()) {
            public void handleMessage(Message msg) {
                super.handleMessage(msg);
                if (msg.what == 1) {
                    Bundle lc = msg.getData();
                    String langCode = lc.getString("lc");
                    Toast.makeText(activity, getString(R.string.langError, langCode), Toast.LENGTH_SHORT).show();
                } else if (msg.what == 2) {
                    Bundle lc = msg.getData();
                    String transString = lc.getString("transString");
                    String serverError = lc.getString("serverError");
                    if (transString == null)
                        Toast.makeText(activity, serverError, Toast.LENGTH_SHORT).show();
                    binding.TranslatedTV.setText(transString);
                    binding.translationPending.setVisibility(View.GONE);
                } else if (msg.what == 3) {
                    Bundle lc = msg.getData();
                    String languages = lc.getString("languages");
                    String serverError = lc.getString("serverError");
                    if (languages == null) {
                        Toast.makeText(activity, serverError, Toast.LENGTH_SHORT).show();
                    } else {
                        List<String> availableLangCodes = new ArrayList<>();
                        String[] str = languages.split(",");
                        for (String s : str) {
                            if (!s.equals(""))
                                availableLangCodes.add(s);
                        }
                        String st = languages;
                        //Setting languages needs to happen before setSourceLang and setTargetLang
                        settings.edit()
                                .putString("languages", st)
                                .apply();

                        List<String> lang = new ArrayList<>();
                        String[] strings = getResources().getStringArray(R.array.Lang);
                        Collections.addAll(lang, strings);
                        //If selected language is not found in newly retrieved languages, replace with default value in UI
                        if (binding.SourceLanguageTop.getText() == "" || !availableLangCodes.contains(getResources().getStringArray(R.array.LangCodes)[lang.indexOf(binding.SourceLanguageTop.getText())])) {
                            sourceLangId = 0;
                            setSourceLang();
                        }
                        if (binding.TargetLanguageTop.getText() == "" || !availableLangCodes.contains(getResources().getStringArray(R.array.LangCodes)[lang.indexOf(binding.TargetLanguageTop.getText())])) {
                            targetLangId = 3;
                            setTargetLang();
                        }
                    }
                }
            }
        };

        binding.SourceText.addTextChangedListener(new TextWatcher() {
            private static final int DELAY_MILLIS = 750;

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

        binding.RemoveSourceText.setOnClickListener(view -> {
            if (!binding.SourceText.getText().toString().equals("")) {
                if (settings.getBoolean("ask", true)) {
                    new MaterialAlertDialogBuilder(activity, R.style.AlertDialog)
                            .setMessage(getString(R.string.rlyRemoveText))
                            .setPositiveButton("Remove", (dialogInterface, i) -> {
                                binding.SourceText.setText("");
                                binding.TranslatedTV.setText("");
                                dialogInterface.dismiss();
                            })
                            .setNeutralButton(getString(R.string.neverAskAgain), (dialogInterface, i) -> {
                                settings.edit()
                                        .putBoolean("ask", false)
                                        .apply();
                                binding.SourceText.setText("");
                                binding.TranslatedTV.setText("");
                                dialogInterface.dismiss();
                            })
                            .show();
                } else {
                    binding.SourceText.setText("");
                    binding.TranslatedTV.setText("");
                }
                InputMethodManager imm = (InputMethodManager) getSystemService(Activity.INPUT_METHOD_SERVICE);
                View view1 = getCurrentFocus();
                imm.hideSoftInputFromWindow(view1.getWindowToken(), 0);
            }

        });

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

        binding.SwitchLanguages.setOnClickListener(view -> {
            int cacheLang = sourceLangId;
            sourceLangId = targetLangId;
            setSourceLang();
            targetLangId = cacheLang;
            setTargetLang();
            translateText();
        });

        binding.SourceLanguageBot.setOnClickListener(view -> chooseLang(true));

        binding.TargetLanguageBot.setOnClickListener(view -> chooseLang(false));

        //About dialog
        binding.info.setOnClickListener(view -> {
            View about = getLayoutInflater().inflate(R.layout.about, null);
            EditText serverET = about.findViewById(R.id.Server);
            EditText apiET = about.findViewById(R.id.Api);
            final String[] server = {settings.getString("server", "https://libretranslate.de")};
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

    private void retrieveLanguages() throws Exception {
        String server = settings.getString("server", "https://libretranslate.de");
        final String[] languages = {""};
        final String[] serverError = {""};
        URL url = new URL(server + "/languages");
        HttpURLConnection connection = server.startsWith("https") ?
                (HttpsURLConnection) url.openConnection() : (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        connection.setRequestProperty("accept", "application/json");
        Thread thread = new Thread(() -> {
            try {
                InputStream inputStream = connection.getInputStream();
                BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
                JSONArray jsonArray = new JSONArray(reader.readLine());
                StringBuilder languagesSB = new StringBuilder();
                List<String> lsb = new ArrayList<>(Arrays.asList(getResources().getStringArray(R.array.LangCodes)));
                for (int i = 0; i < jsonArray.length(); i++) {
                    String langCode = jsonArray.getJSONObject(i).getString("code");
                    if (lsb.contains(langCode))
                        languagesSB.append(langCode).append(",");
                    else {
                        Bundle bundle = new Bundle();
                        bundle.putString("lc", langCode);
                        Message msg = new Message();
                        msg.setData(bundle);
                        msg.what = 1;
                        mhandler.sendMessage(msg);
                    }
                }
                if (languagesSB.length() != 0)
                    languagesSB.setLength(languagesSB.length() - 1);
                languages[0] = languagesSB.toString();
            } catch (Exception e) {
                e.printStackTrace();
                try {
                    serverError[0] = new JSONObject(String.valueOf(connection.getErrorStream().read())).getString("error");
                } catch (Exception ee) {
                    ee.printStackTrace();
                    getString(R.string.netError);
                }
            } finally {
                connection.disconnect();
            }
            Bundle bundle = new Bundle();
            bundle.putString("languages", languages[0]);
            bundle.putString("serverError", serverError[0]);
            Message msg = new Message();
            msg.setData(bundle);
            msg.what = 3;
            mhandler.sendMessage(msg);
        });
        thread.start();
    }

    private void translateText() {
        MainActivity activity = this;
        String languages = settings.getString("languages", "");
        final String[][] serverError = {{""}};
        final String[] transString = {""};
        List<String> availableLangCodes = new ArrayList<>();
        if (!(binding.SourceText.getText().toString().equals("") || languages.equals(""))) {//fix
            String server = settings.getString("server", "https://libretranslate.de");
            String apiKey = settings.getString("apiKey", "");
            HttpURLConnection connection = null;
            try {
                URL url = new URL(server + "/translate");
                String[] str = languages.split(",");
                Collections.addAll(availableLangCodes, str);
                connection = server.startsWith("https") ?
                        (HttpsURLConnection) url.openConnection() : (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("POST");
            } catch (IOException e) {
                e.printStackTrace();
            }
            if (connection != null) {
                connection.setRequestProperty("accept", "application/json");
            }
            String q = binding.SourceText.getText().toString().replace("&", "%26");
            String source = availableLangCodes.get(sourceLangId);
            String target = availableLangCodes.get(targetLangId);
            String data = "q=" + q + "&source=" + source + "&target=" + target;
            if (!apiKey.equals("")) {
                data += "&api_key=" + apiKey;
            }
            byte[] out = data.getBytes(StandardCharsets.UTF_8);
            HttpURLConnection finalConnection = connection;
            Thread thread = new Thread(() -> {
                try {
                    if (finalConnection != null) {
                        OutputStream stream = finalConnection.getOutputStream();
                        stream.write(out);
                        InputStream inputStream = new DataInputStream(finalConnection.getInputStream());
                        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
                        transString[0] = new JSONObject(reader.readLine()).getString("translatedText");
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    transString[0] = null;
                    try {
                        serverError[0][0] = new JSONObject(String.valueOf(finalConnection.getErrorStream().read())).getString("error");
                    } catch (Exception ee) {
                        ee.printStackTrace();
                        getString(R.string.netError);
                    }
                } finally {
                    finalConnection.disconnect();
                }
                Bundle bundle = new Bundle();
                bundle.putString("transString", transString[0]);
                bundle.putString("serverError", serverError[0][0]);
                Message msg = new Message();
                msg.setData(bundle);
                msg.what = 2;
                mhandler.sendMessage(msg);
            });
            thread.start();
        }
    }

    private void setSourceLang() {
        String languages = settings.getString("languages", "");
        if (!languages.equals("")) {
            String[] serverLangCodes = languages.split(",");
            List<String> availableLangCodes = new ArrayList<>();
            List<String> localLangCodes = new ArrayList<>();
            Collections.addAll(availableLangCodes, serverLangCodes);
            if (availableLangCodes.size() <= sourceLangId)
                sourceLangId = 0;
            Collections.addAll(localLangCodes, getResources().getStringArray(R.array.LangCodes));

            String sourceLang =
                    getResources().getStringArray(R.array.Lang)[localLangCodes
                            .indexOf(
                                    availableLangCodes.get(sourceLangId)
                            )];
            binding.SourceLanguageTop.setText(sourceLang);
            binding.SourceLanguageBot.setText(sourceLang);
            settings.edit()
                    .putInt("Source", sourceLangId)
                    .apply();
        }
    }

    private void setTargetLang() {
        String languages = settings.getString("languages", "");
        if (!languages.equals("")) {
            String[] serverLangCodes = languages.split(",");
            List<String> availableLangCodes = new ArrayList<>();
            List<String> localLangCodes = new ArrayList<>();
            Collections.addAll(availableLangCodes, serverLangCodes);
            if (availableLangCodes.size() <= targetLangId)
                targetLangId = 0;
            Collections.addAll(localLangCodes, getResources().getStringArray(R.array.LangCodes));

            String targetLang =
                    getResources().getStringArray(R.array.Lang)[localLangCodes
                            .indexOf(
                                    availableLangCodes.get(targetLangId)
                            )];
            binding.TargetLanguageTop.setText(targetLang);
            binding.TargetLanguageBot.setText(targetLang);
            settings.edit()
                    .putInt("Target", targetLangId)
                    .apply();
        }
    }

    private void chooseLang(Boolean source) {
        String languages = settings.getString("languages", "");
        List<String> availableLangs = new ArrayList<>();
        //Check if available languages exist in strings.xml. If so, place in availableLangs list
        if (!languages.equals("")) {
            List<String> availableLangCodes = new ArrayList<>();
            List<String> list = new ArrayList<>();
            Collections.addAll(availableLangCodes, languages.split(","));
            Collections.addAll(list, getResources().getStringArray(R.array.LangCodes));
            for (int i = 0; i < availableLangCodes.size(); i++) {
                availableLangs.add(
                        getResources().getStringArray(R.array.Lang)[list
                                .indexOf(availableLangCodes.get(i))]
                );
            }
        }

        new AlertDialog.Builder(
                this, R.style.AlertDialog
        )
                .setTitle(getString(R.string.chooseLang))
                .setItems(availableLangs.toArray(new String[availableLangs.size()]), (dialog, which) -> {
                    if (source) {
                        sourceLangId = which;
                        setSourceLang();
                    } else {
                        targetLangId = which;
                        setTargetLang();
                    }
                    translateText();
                })
                .setPositiveButton(getString(R.string.abort), (dialog, which) -> dialog.cancel())
                .show();
    }
}