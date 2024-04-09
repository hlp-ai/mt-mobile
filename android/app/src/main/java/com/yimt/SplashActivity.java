package com.yimt;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.widget.Button;
import androidx.appcompat.app.AppCompatActivity;

public class SplashActivity extends AppCompatActivity {

    private static final int SPLASH_DISPLAY_LENGTH = 5000; // 延迟三秒

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash); // 设置启动画面的布局

        Button skipButton = findViewById(R.id.skip_button);

        new CountDownTimer(SPLASH_DISPLAY_LENGTH, 1000) {
            @SuppressLint("SetTextI18n")
            public void onTick(long millisUntilFinished) {
                skipButton.setText("跳过 (" + millisUntilFinished / 1000 + ")");
            }

            public void onFinish() {
                skipButton.setText("跳过");
                goToMainActivity();
            }
        }.start();

        skipButton.setOnClickListener(v -> goToMainActivity());
    }

    private void goToMainActivity() {
        Intent mainIntent = new Intent(SplashActivity.this, MainActivity.class);
        SplashActivity.this.startActivity(mainIntent);
        SplashActivity.this.finish();
    }
}