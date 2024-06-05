package com.yimt;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.widget.TextView;

public class FlashActivity extends AppCompatActivity {

    private TextView adView;
    private TextView timeView;

    private Handler handler = new Handler();

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

        adView = findViewById(R.id.AD);
        adView.setText("广告内容展示区...");

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
}