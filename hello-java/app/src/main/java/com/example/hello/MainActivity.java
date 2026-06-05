package com.example.hello;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

public class MainActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // XMLレイアウトファイルを画面にセットする
        setContentView(R.layout.activity_main);

        // XMLで定義したTextViewの部品を取得する
        TextView textView = findViewById(R.id.text_view);

        // タップされた時の処理
        textView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish(); // アプリを終了する
            }
        });
        
    }
}
