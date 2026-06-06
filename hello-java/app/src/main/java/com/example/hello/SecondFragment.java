package com.example.hello;

import androidx.activity.OnBackPressedCallback;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

public class SecondFragment extends Fragment {

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        // fragment_second.xml を読み込んで実体化
        return inflater.inflate(R.layout.fragment_second, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // 戻るボタンの操作を横取り（ブロック）するコールバック
        OnBackPressedCallback callback = new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                // ここを空にすることで、戻る操作が無視（ブロック）されます。
                // ※独自のステートマシンで前の画面に戻したい場合は、ここに独自の遷移処理を書きます。
            }
        };
        // Fragmentのライフサイクルに合わせてコールバックを登録
        requireActivity().getOnBackPressedDispatcher().addCallback(getViewLifecycleOwner(), callback);

        // 「アプリを終了する」ボタンの処理
        Button buttonFinish = view.findViewById(R.id.button_finish);
        buttonFinish.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                requireActivity().finishAndRemoveTask();
            }
        });
    }
}
