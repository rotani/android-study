package com.example.hello;

import android.app.Activity;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.TextView;

public class MainActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // 画面に表示するテキストを作る
        TextView textView = new TextView(this);
        textView.setText("Hello, Android World! (Java版)\n\n画面をタップすると終了します");
        textView.setTextSize(24f);
        textView.setGravity(Gravity.CENTER);
        
        // タップされた時の処理
        textView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish(); // アプリを終了する
            }
        });
        
        // テキストを画面にセットする
        setContentView(textView);
    }
}
