package com.yimt;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.widget.TextView;
import android.widget.Toast;
import org.json.JSONObject;


public class FlashActivity extends AppCompatActivity {

    private TextView adView;
    private TextView timeView;

    private Handler handler = new Handler();

    private SharedPreferences settings;
    private final static String DEFAULT_SERVER = "http://192.168.1.104:5555";

    private static final int TIMEOUT = 4;
    private int left = TIMEOUT;

    private String ad_id = "";
    private String ad_url = "";

    private Runnable startRunnable = new Runnable() {

        @Override
        public void run() {
            Intent mainIntent = new Intent(FlashActivity.this, MainActivity.class);
            startActivity(mainIntent);
            finish();
        }
    };

    private Runnable timeRunnable = new Runnable() {

        @Override
        public void run() {
            left = left - 1;
            timeView.setText("跳过 " + left);
            handler.postDelayed(this, 1000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_flash);

        // 获取服务器信息
        settings = getSharedPreferences("com.yimt", 0);
        String server = settings.getString("server", DEFAULT_SERVER);
        String apiKey = settings.getString("apiKey", "");

        adView = findViewById(R.id.AD);

        adView.setOnClickListener(view -> {
            if(ad_id.length()>0 && ad_url.length()>0) {
                String url = server + "/click_ad?ad_id=" + ad_id + "&platform=app" + "&url=" + ad_url;
                openWebPage(url);
            }
        });

        getAD();

        timeView = findViewById(R.id.TIME);
        timeView.setText("跳过 " + left);
        timeView.setOnClickListener(view -> {
            Intent mainIntent = new Intent(FlashActivity.this, MainActivity.class);
            startActivity(mainIntent);

            handler.removeCallbacks(startRunnable);
            handler.removeCallbacks(timeRunnable);

            finish();
        });

        // 广告展示倒计时
        handler.postDelayed(timeRunnable, 1000);

        // 倒计时结束后打开主页面
        handler.postDelayed(startRunnable, TIMEOUT * 1000);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        handler.removeCallbacks(startRunnable);
        handler.removeCallbacks(timeRunnable);
    }

    // 请求和展示广告
    private void getAD() {
        String server = settings.getString("server", DEFAULT_SERVER);
        String apiKey = settings.getString("apiKey", "");

        Thread thread = new Thread(() -> {
            try {
                final JSONObject adMsg = requestAD(server);
                final String ad = adMsg.getString("content");

                ad_id = adMsg.getString("ad_id");
                ad_url = adMsg.getString("url");

                runOnUiThread(()->adView.setText(ad));
            } catch (Exception e) {
                ad_url = "";
                ad_id = "";

                e.printStackTrace();
                final String error = e.toString();

                runOnUiThread(()->Toast.makeText(FlashActivity.this, error, Toast.LENGTH_LONG).show());
            }
        });

        thread.start();
    }

    // 请求广告
    private JSONObject requestAD(String server) throws Exception {
        String url = server + "/request_ad";

        JSONObject json = new JSONObject();
        json.put("platform", "app");

        return Utils.requestService(url, json.toString());
    }

    public void openWebPage(String url) {
        Uri webpage = Uri.parse(url);
        Intent intent = new Intent(Intent.ACTION_VIEW, webpage);
        if (intent.resolveActivity(getPackageManager()) != null) {
            startActivity(intent);
        }
    }
}