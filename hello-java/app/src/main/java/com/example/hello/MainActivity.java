package com.example.hello;

import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity; // 通常の Activity ではなくこちらを使う

public class MainActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // 空箱のレイアウトをセット
        setContentView(R.layout.activity_main);

        // 初回起動時のみ、空箱（fragment_container）の中に MainFragment をはめ込む
        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.fragment_container, new MainFragment())
                    .commit();
        }
    }
}
