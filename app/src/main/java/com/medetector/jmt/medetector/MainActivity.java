package com.medetector.jmt.medetector;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

public class MainActivity extends Activity implements View.OnClickListener{

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Button button_imageSearch = (Button) findViewById(R.id.button_imageSearch);
        Button button_categorySearch = (Button) findViewById(R.id.button_categorySearch);
        Button button_textSearch = (Button) findViewById(R.id.button_textSearch);
        Button button_myPage = (Button) findViewById(R.id.button_myPage);

        button_imageSearch.setOnClickListener(this);
        button_categorySearch.setOnClickListener(this);
        button_textSearch.setOnClickListener(this);
        button_myPage.setOnClickListener(this);
    }

    @Override
    public void onClick(View v) {
        // define which methods to call when buttons in view clicked
        int id = v.getId();

        switch(id) {
            case R.id.button_imageSearch:
                Intent intent = new Intent(MainActivity.this, ImageSearchActivity.class);
                startActivity(intent);
                break;
            case R.id.button_categorySearch:
                Toast.makeText (MainActivity.this, "Coming Soon", Toast.LENGTH_SHORT).show();
                break;
            case R.id.button_textSearch:
                Toast.makeText (MainActivity.this, "Coming Soon", Toast.LENGTH_SHORT).show();
            case R.id.button_myPage:
                Toast.makeText (MainActivity.this, "Coming Soon", Toast.LENGTH_SHORT).show();
                break;
            default:
                break;
        }
    }
}
