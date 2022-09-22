package com.example.test4;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;

import androidx.appcompat.app.AppCompatActivity;

import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.service.autofill.RegexValidator;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.method.LinkMovementMethod;
import android.view.View;

import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;

import com.example.test4.databinding.ActivityMainBinding;

import android.view.Menu;
import android.view.MenuItem;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Stack;
import java.util.Vector;
import java.util.regex.Pattern;

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
        setTheme(theme());
        super.onCreate(savedInstanceState);

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        if(settings.getBoolean("shrink", false))
            getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);

        try {
            retrieveLanguages();
        } catch (Exception e) {
            e.printStackTrace();
        }

        String languages = settings.getString("languages", "22").toString();

        if(languages != ""){
            String[] split = languages.split(",");

            List<String> availableLangCodes = new ArrayList<String>();

            for(String str : split){
                availableLangCodes.add(str);
            }

            sourceLangId = settings.getInt("Source", 0);
            setSourceLang();
            targetLangId = settings.getInt("Target", availableLangCodes.size()-1);
            setTargetLang();

        }
        Intent intent = getIntent();
        if (intent.getAction() == Intent.ACTION_SEND) {
            if (intent.getExtras() != null){
                String s = getString(Integer.parseInt(Intent.EXTRA_TEXT));
                binding.SourceText.setText(s);
            }
                translateText();
        }
        new Handler(Looper.getMainLooper());
        mhandler = new Handler(Looper.getMainLooper()){
            public void handleMessage(Message msg){
                super.handleMessage(msg);
                Bundle lc = msg.getData();
                String langCode = lc.getString("lc");
                switch (msg.what){
                    case 1:
                        Toast.makeText(activity, getString(R.string.langError, langCode), Toast.LENGTH_SHORT).show();
                        break;

                }
            }
        };

        binding.SourceText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {

            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {

            }
            Handler handler = new Handler(Looper.getMainLooper());
            Runnable workRunnable = new Runnable() {
                @Override
                public void run() {
                        translateText();
                }
            };
            @Override
            public void afterTextChanged(Editable editable) {
                handler.removeCallbacks(workRunnable);
                handler.postDelayed(workRunnable,750);
                binding.translationPending.setVisibility(View.VISIBLE);
            }
        });
        binding.RemoveSourceText.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                if(binding.SourceText.getText().toString()!=""){
                    if (settings.getBoolean("ask", true)){
                        new MaterialAlertDialogBuilder(activity, R.style.AlertDialog)
                                .setMessage(getString(R.string.rlyRemoveText))
                                .setPositiveButton("Remove", new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialogInterface, int i) {
                                        binding.SourceText.setText("");
                                        binding.TranslatedTV.setText("");
                                        dialogInterface.dismiss();
                                    }
                                })
                                .setNeutralButton(getString(R.string.neverAskAgain), new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialogInterface, int i) {
                                        settings.edit()
                                                .putBoolean("ask", false)
                                                .apply();
                                        binding.SourceText.setText("");
                                        binding.TranslatedTV.setText("");
                                        dialogInterface.dismiss();
                                    }
                                })
                                .show();
                    }else {
                        binding.SourceText.setText("");
                        binding.TranslatedTV.setText("");
                    }
                    InputMethodManager imm = (InputMethodManager)getSystemService(Activity.INPUT_METHOD_SERVICE);
                    View view1 = getCurrentFocus();
                    imm.hideSoftInputFromWindow(view1.getWindowToken(),0);
                }

            }
        });

        binding.CopyTranslation.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (binding.TranslatedTV.getText().toString() != ""){
                    ClipboardManager clipboard = (ClipboardManager)getSystemService(Context.CLIPBOARD_SERVICE);
                    ClipData clip = ClipData.newPlainText("translated text", binding.TranslatedTV.getText());
                    clipboard.setPrimaryClip(clip);
                    Snackbar.make(
                            binding.CopyTranslation,
                            getString(R.string.copiedClipboard),
                            Snackbar.LENGTH_LONG
                    ).show();
                }
            }
        });

        binding.ShareTranslation.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (binding.TranslatedTV.getText().toString()!= ""){
                    startActivity(Intent.createChooser(new Intent(),null)
                            .setAction(Intent.ACTION_SEND)
                            .setType("text/plain")
                            .putExtra(Intent.EXTRA_TEXT,binding.TranslatedTV.getText())
                    );
                }
            }
        });

        binding.SwitchLanguages.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                int cacheLang = sourceLangId;
                sourceLangId = targetLangId;
                setSourceLang();
                targetLangId = cacheLang;
                setTargetLang();
                translateText();
            }
        });

        binding.SourceLanguageBot.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                chooseLang(true);
            }
        });

        binding.TargetLanguageBot.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                chooseLang(false);
            }
        });

        //theme switcher
        binding.themeSwitcher.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                int newTheme = settings.getInt("Theme", 1) + 1;
                if (newTheme == 5) {
                    newTheme = 0;
                }
                settings.edit()
                        .putInt("Theme", newTheme)
                        .apply();
                finish();
                startActivity(intent);
            }
        });

        //About dialog
        binding.info.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                View about = getLayoutInflater().inflate(R.layout.about,null);
                EditText serverET = (EditText)about.findViewById(R.id.Server);
                EditText apiET = (EditText)about.findViewById(R.id.Api);
                CheckBox shrinkCB =(CheckBox)about.findViewById(R.id.Shrink);
                TextView tv1 = about.findViewById(R.id.aboutTV1);
                TextView tv2 = about.findViewById(R.id.aboutTV2);
                TextView tv3 = about.findViewById(R.id.aboutTV3);
                final String[] server = {settings.getString("server", "libretranslate.de")};
                String apiKey = settings.getString("apiKey","");
                Boolean shrink = settings.getBoolean("shrink", false);
                serverET.setText(server[0]);
                apiET.setText(apiKey);
                shrinkCB.setChecked(shrink);
                tv1.setMovementMethod(LinkMovementMethod.getInstance());
                tv2.setMovementMethod(LinkMovementMethod.getInstance());
                tv3.setMovementMethod(LinkMovementMethod.getInstance());
                AlertDialog.Builder popUp = new AlertDialog.Builder(activity, R.style.AlertDialog);
                popUp.setView(about)
                        .setTitle(getString(R.string.app_name))
                        .setPositiveButton("Save", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i) {
                                server[0] = serverET.getText().toString().replace("http://", "")
                                        .replace("https://", "")
                                        .replace("www.", "")
                                        .replace("/translate", "");
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

                                if (shrink != shrinkCB.isChecked()) {
                                    settings.edit()
                                            .putBoolean("shrink", shrinkCB.isChecked())
                                            .apply();
                                    finish();
                                    startActivity(intent);
                                }
                            }
                        })
                        .setNegativeButton(getString(R.string.close),null)
                        .show();
            }
        });




    }

    private void retrieveLanguages() throws Exception {
        String server = settings.getString("server", "libretranslate.de");
        final String[] languages = {""};
        final String[] serverError = {""};
            URL url = new URL("https://"+server+"/languages");
            HttpsURLConnection connection = (HttpsURLConnection)url.openConnection();
            connection.setRequestMethod("GET");
            connection.setRequestProperty("accept", "application/json");
        Thread thread= new Thread(new Runnable() {
            @Override
            public void run() {
                //String languages = "";
                //String serverError = "";
                try {
                    InputStream inputStream = connection.getInputStream();
                    BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
                    JSONArray jsonArray = new JSONArray(reader.readLine());
                    StringBuilder languagesSB = new StringBuilder();
                    List<String> lsb = new ArrayList<>();
                    for (String ss : getResources().getStringArray(R.array.LangCodes)){
                        lsb.add(ss);
                    }
                    for (int i = 0;i<jsonArray.length();i++){
                        String langCode = jsonArray.getJSONObject(i).getString("code");
                        if (lsb.contains(langCode))
                            languagesSB.append(langCode).append(",");
                        else{
                            Bundle bundle = new Bundle();
                            bundle.putString("lc",langCode);
                            Message msg = new Message();
                            msg.setData(bundle);
                            msg.what = 1;
                            mhandler.sendMessage(msg);
                        }
                    }
                    if(languagesSB.length()!=0)
                        languagesSB.setLength(languagesSB.length()-1);
                    languages[0] = languagesSB.toString();
                    connection.disconnect();
                } catch (Exception e) {
                    e.printStackTrace();
                    try {
                        serverError[0] = new JSONObject(String.valueOf(connection.getErrorStream().read())).getString("error");
                    }
                    catch(Exception ee) {
                        ee.printStackTrace();
                        getString(R.string.netError);
                    }
                }
        }});
        thread.start();
        thread.join();
        if (languages[0]== null) {
            Toast.makeText(this, serverError[0], Toast.LENGTH_SHORT).show();
        } else {
            List<String> availableLangCodes = new ArrayList<>();
            String[] str = languages[0].split(",");
            for (String s : str){
                if (s != "")
                availableLangCodes.add(s);
            }
            String st = languages[0];
            //Setting languages needs to happen before setSourceLang and setTargetLang
            settings.edit()
                    .putString("languages", st)
                    .apply();

            List<String> lang = new ArrayList<>();
            String[] strings=getResources().getStringArray(R.array.Lang);
            for (String ss : strings){
                lang.add(ss);
            }
            //If selected language is not found in newly retrieved languages, replace with default value in UI
            if (binding.SourceLanguageTop.getText() == "" || ! availableLangCodes.contains(getResources().getStringArray(R.array.LangCodes)[lang.indexOf(binding.SourceLanguageTop.getText())])) {
                sourceLangId = 0;
                setSourceLang();
            }
            if (binding.TargetLanguageTop.getText() == "" || ! availableLangCodes.contains(getResources().getStringArray(R.array.LangCodes)[lang.indexOf(binding.TargetLanguageTop.getText())])) {
                targetLangId = availableLangCodes.size()-1;
                setTargetLang();
            }
        }
    }

    private void translateText() {
        MainActivity activity = this;
        String languages = settings.getString("languages", "").toString();
        final String[][] serverError = {{""}};
        final String[] transString = {""};
        List<String> availableLangCodes = new ArrayList<>();
        if (binding.SourceText.getText().toString() != "" && languages != ""){
            String server = settings.getString("server", "libretranslate.de");
            String apiKey = settings.getString("apiKey", "");
            HttpsURLConnection connection = null;
            try {
                URL url = new URL("https://"+server+"/translate");
                String[] str = languages.split(",");
                for (String s:str)
                    availableLangCodes.add(s);
                connection = (HttpsURLConnection)url.openConnection();
                connection.setRequestMethod("POST");
            } catch (IOException e) {
                e.printStackTrace();
            }
            connection.setRequestProperty("accept", "application/json");
            String q = binding.SourceText.getText().toString().replace("&","%26");
            String source = availableLangCodes.get(sourceLangId);
            String target = availableLangCodes.get(targetLangId);
            String data = "q="+q+"&source="+source+"&target="+target;
            if (apiKey != ""){
                data += "&api_key="+apiKey;
            }
            byte[] out = data.getBytes(StandardCharsets.UTF_8);
            /*
            val data =
                    "q=${binding.SourceText.text.replace(Regex("&"), "%26")}" +
                    "&source=${availableLangCodes[sourceLangId]}" +
                    "&target=${availableLangCodes[targetLangId]}" +
            if (apiKey != "") {
                "&api_key=$apiKey"
            } else {
                ""
            }
            val out = data.toByteArray(Charsets.UTF_8)
            @Suppress("BlockingMethodInNonBlockingContext")
            */
            HttpsURLConnection finalConnection = connection;
            Thread thread = new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        OutputStream stream = finalConnection.getOutputStream();
                        stream.write(out);
                        InputStream inputStream = new DataInputStream(finalConnection.getInputStream());
                        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
                        transString[0] = new JSONObject(reader.readLine()).getString("translatedText");
                    } catch (Exception e) {
                        e.printStackTrace();
                        transString[0] = null;
                        try {
                            serverError[0][0] = new JSONObject(String.valueOf(finalConnection.getErrorStream().read())).getString("error");
                        }
                        catch(Exception ee) {
                            ee.printStackTrace();
                            getString(R.string.netError);
                        }
                    }
                }
            });
            try {
                thread.start();
                thread.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            if (transString[0] == null)
                Toast.makeText(activity, serverError[0][0], Toast.LENGTH_SHORT).show();
            binding.TranslatedTV.setText(transString[0]);
            binding.translationPending.setVisibility(View.GONE);
        }
    }

    private void setSourceLang(){
        String languages = settings.getString("languages", "").toString();
        if (languages != "") {
            String[] str = languages.split(",");
            List<String> availableLangCodes = new ArrayList<>();
            List<String> list = new ArrayList<String>();
            for (String s:str)
                availableLangCodes.add(s);
            if (availableLangCodes.size() <= sourceLangId)
                sourceLangId = 0;
            for (String ss : getResources().getStringArray(R.array.LangCodes)) {
                list.add(ss);
            }

            String sourceLang =
                    getResources().getStringArray(R.array.Lang)[list
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

    private void setTargetLang(){
        String languages = settings.getString("languages", "").toString();
        if (languages != "") {
            String[] str = languages.split(",");
            List<String> availableLangCodes = new ArrayList<>();
            List<String> list = new ArrayList<>();
            for (String s:str)
                availableLangCodes.add(s);
            if (availableLangCodes.size() <= targetLangId)
                targetLangId = 0;
            for (String s : getResources().getStringArray(R.array.LangCodes)) {
                list.add(s);
            }

            String targetLang =
                    getResources().getStringArray(R.array.Lang)[list
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

    private void chooseLang(Boolean source){
        String languages = settings.getString("languages", "").toString();
        List<String> availableLangs = new ArrayList<String>();
        //Check if available languages exist in strings.xml. If so, place in availableLangs list
        if (languages != "") {
            List<String> availableLangCodes = new ArrayList<String>();
            List<String> list = new ArrayList<String>();
            for (String s:languages.split(","))
                availableLangCodes.add(s);
            for (String s:getResources().getStringArray(R.array.LangCodes))
                list.add(s);
            for (int i = 0;i<availableLangCodes.size();i++){
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
                .setItems(availableLangs.toArray(new String[availableLangs.size()]), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (source) {
                            sourceLangId = which;
                            setSourceLang();
                        } else {
                            targetLangId = which;
                            setTargetLang();
                        }
                        translateText();
                    }
                })
                .setPositiveButton(getString(R.string.abort), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.cancel();
                    }
                })
                .show();
    }

    private int theme(){
        settings = getSharedPreferences("de.beowulf.libretranslater", 0);
        int res = 0;
        switch (settings.getInt("Theme", 1)){
            case 1: res = R.style.DarkTheme; break;
            case 2: res = R.style.LilaTheme; break;
            case 3: res = R.style.SandTheme; break;
            case 4: res = R.style.BlueTheme; break;
            default: res = R.style.LightTheme; break;
        }
        return res;
    }
}