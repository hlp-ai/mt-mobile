package com.yimt;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONException;
import org.json.JSONObject;

public class FlashActivity extends AppCompatActivity {

    private TextView adView;
    private TextView timeView;

    private Handler handler = new Handler();

    private SharedPreferences settings;
    private final static String DEFAULT_SERVER = "http://192.168.1.104:5555";

    private static final int TIMEOUT = 4;
    private int left = TIMEOUT;

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
            timeView.setText(left + "秒");
            handler.postDelayed(this, 1000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_flash);

        settings = getSharedPreferences("com.yimt", 0);
        String server = settings.getString("server", DEFAULT_SERVER);
        String apiKey = settings.getString("apiKey", "");

        String ad = "广告待展示...";

        JSONObject adMsg = new JSONObject();
        try {
            adMsg = requestAD(server);

            String adID = adMsg.getString("ad_id");
            ad = adMsg.getString("content");
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(FlashActivity.this, "连接不上服务器", Toast.LENGTH_SHORT).show();
        }

        adView = findViewById(R.id.AD);
        adView.setText(ad);

        timeView = findViewById(R.id.TIME);
        timeView.setText(left + "秒");

        handler.postDelayed(timeRunnable, 1000);
        handler.postDelayed(startRunnable, TIMEOUT * 1000);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        handler.removeCallbacks(startRunnable);
        handler.removeCallbacks(timeRunnable);
    }

//    private void getAD() {
//        String server = settings.getString("server", DEFAULT_SERVER);
//        String apiKey = settings.getString("apiKey", "");
//
//        Thread thread = new Thread(() -> {
//            String error = "";
//            JSONObject adMsg = new JSONObject();
//            try {
//                adMsg = requestAD(server);
//            } catch (Exception e) {
//                e.printStackTrace();
//                error = e.toString();
//            }
//
//            Bundle bundle = new Bundle();
//            bundle.putString("error", error);
//            if(error.isEmpty()){
//                try {
//                    bundle.putString("ad_id", adMsg.getString("ad_id"));
//                    bundle.putString("type", adMsg.getString("type"));
//                    bundle.putString("content", adMsg.getString("content"));
//                    bundle.putString("url", adMsg.getString("url"));
//                } catch (JSONException e) {
//                    throw new RuntimeException(e);
//                }
//            }
//            Message msg = new Message();
//            msg.setData(bundle);
//            msg.what = GET_AD;
//            mhandler.sendMessage(msg);
//        });
//        thread.start();
//    }

    private JSONObject requestAD(String server) throws Exception {
        String url = server + "/request_ad";

        JSONObject json = new JSONObject();
        json.put("platform", "app");

        JSONObject responseJson = Utils.requestService(url, json.toString());

        return responseJson;
    }
}