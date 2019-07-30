package com.example.nrtpttv2;

import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;
import android.widget.TextView;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import android.util.Log;

public class MainActivity extends AppCompatActivity {
    
    public TextView display_text;
    HashMap<String, String> params;

    public native void startNdnRtc(Map<String, String> params);

    static {
        System.loadLibrary("ndnrtc-wrapper");
    }
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        display_text = (TextView) findViewById(R.id.display_text);

	params = new HashMap<>();
	params.put("homePath", getFilesDir().getAbsolutePath());
	Set<Map.Entry<String, String>> e = params.entrySet();

	startNdnRtc(params);
    }

}
