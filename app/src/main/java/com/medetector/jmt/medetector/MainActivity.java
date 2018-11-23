package com.medetector.jmt.medetector;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;


public class MainActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
    }

    public void ClickButton1(View v) {
        Toast.makeText(getApplicationContext(), "이미지를 눌렀습니다.", Toast.LENGTH_LONG).show();
    }
}
