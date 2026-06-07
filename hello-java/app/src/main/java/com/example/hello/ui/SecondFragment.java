package com.example.hello.ui;

import androidx.activity.OnBackPressedCallback;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import com.example.hello.databinding.FragmentSecondBinding;
import com.example.hello.viewmodel.MainViewModel;

public class SecondFragment extends Fragment {

    private FragmentSecondBinding binding;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentSecondBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // ViewModelから保存された名前を受け取って表示する
        MainViewModel viewModel = new ViewModelProvider(requireActivity()).get(MainViewModel.class);
        String userName = viewModel.getUserName();
        binding.textGreeting.setText("こんにちは、" + userName + "さん！");

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
        binding.buttonFinish.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                requireActivity().finishAndRemoveTask();
            }
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
