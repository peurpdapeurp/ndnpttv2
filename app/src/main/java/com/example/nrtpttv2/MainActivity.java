package com.example.nrtpttv2;

import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;
import android.widget.TextView;

public class MainActivity extends AppCompatActivity {

    public TextView display_text;

    public native String helloWorld();

    static {
        System.loadLibrary("test");
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        display_text = (TextView) findViewById(R.id.display_text);

        display_text.setText(helloWorld());

    }

}
