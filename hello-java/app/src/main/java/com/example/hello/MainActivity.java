package com.example.hello;

import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity; // 通常の Activity ではなくこちらを使う
import androidx.lifecycle.Observer;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

public class MainActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // 空箱のレイアウトをセット
        setContentView(R.layout.activity_main);

        // ViewModelを取得する
        MainViewModel viewModel = new ViewModelProvider(this).get(MainViewModel.class);

        // ViewModelの「状態（AppState）」を監視（Observe）する
        viewModel.getAppState().observe(this, new Observer<AppState>() {
            @Override
            public void onChanged(AppState state) {
                Fragment currentFragment = getSupportFragmentManager().findFragmentById(R.id.fragment_container);

                Fragment targetFragment = null;

                switch (state) {
                    case PAGE_1:
                        if (!(currentFragment instanceof MainFragment)) {
                            targetFragment = new MainFragment();
                        }
                        break;
                    case PAGE_2:
                        if (!(currentFragment instanceof SecondFragment)) {
                            targetFragment = new SecondFragment();
                        }
                        break;
                }

                // targetFragment が null ではない（＝新しく new された）場合のみ入れ替えを実行する
                if (targetFragment != null) {
                    getSupportFragmentManager().beginTransaction().replace(R.id.fragment_container, targetFragment).commit();
                }
            }
        });
    }
}
