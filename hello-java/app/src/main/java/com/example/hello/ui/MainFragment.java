package com.example.hello.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import com.example.hello.databinding.FragmentMainBinding;
import com.example.hello.viewmodel.MainViewModel;

public class MainFragment extends Fragment {

    // View Bindingのインスタンスを保持する変数
    private FragmentMainBinding binding;

    // 1. 画面の見た目（XML）を読み込んで実体化するメソッド
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        // XMLをインフレートし、bindingオブジェクトを生成する
        binding = FragmentMainBinding.inflate(inflater, container, false);
        return binding.getRoot(); // 実体化した一番親のViewを返す
    }

    // 2. 画面が作られた直後に呼ばれるメソッド（ボタンの処理などはここに書く）
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        // MainActivityが持っているのと同じ ViewModel を取得する
        MainViewModel viewModel = new ViewModelProvider(requireActivity()).get(MainViewModel.class);

        // ViewModelからエラー状態を監視
        viewModel.getErrorMessage().observe(getViewLifecycleOwner(), new Observer<String>() {
            @Override
            public void onChanged(String errorMsg) {
                if (errorMsg != null) {
                    // binding 経由で直接部品にアクセスする
                    binding.textError.setText(errorMsg);
                    binding.textError.setVisibility(View.VISIBLE);
                } else {
                    binding.textError.setVisibility(View.GONE);
                }
            }
        });

        binding.buttonNext.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // 入力された文字をViewModelに渡す
                viewModel.submitName(binding.editTextName.getText().toString());
            }
        });
    }

    // Fragmentのビューが破棄される時にbindingを空にする（メモリリーク対策の鉄則）
    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
