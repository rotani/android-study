package com.example.hello;

import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity; // 通常の Activity ではなくこちらを使う
import com.example.hello.ui.MainFragment;

public class MainActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // 空箱のレイアウトをセット
        setContentView(R.layout.activity_main);

        // 今回から単一画面の音声アシスタントになるため、画面切り替えは廃止。
        // 初回起動時のみ MainFragment をセットする。
        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction().replace(R.id.fragment_container, new MainFragment()).commit();
        }
    }
}
