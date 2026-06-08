package com.example.hello.ui;

import android.os.Bundle;
import android.Manifest;
import android.content.pm.PackageManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.graphics.Color;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import com.example.hello.databinding.FragmentMainBinding;
import com.example.hello.viewmodel.MainViewModel;
import com.example.hello.viewmodel.AppState;

public class MainFragment extends Fragment {

    // View Bindingのインスタンスを保持する変数
    private FragmentMainBinding binding;

    // 権限要求の結果を受け取るランチャー
    private ActivityResultLauncher<String> requestPermissionLauncher;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // 権限を要求した結果（許可されたか、拒否されたか）を受け取るコールバックを登録
        requestPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                isGranted -> {
                    if (isGranted) {
                        // 許可されたら、ViewModelにマイクボタンが押されたことを伝える（録音開始）
                        MainViewModel viewModel = new ViewModelProvider(requireActivity()).get(MainViewModel.class);
                        viewModel.onMicButtonClicked();
                    } else {
                        // 拒否された場合はトースト（小さなポップアップ）で警告する
                        Toast.makeText(requireContext(), "マイクの権限が許可されないと録音できません", Toast.LENGTH_SHORT).show();
                    }
                });
    }

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

        viewModel.getAppState().observe(getViewLifecycleOwner(), state -> {
            binding.textStatus.setText(state.name());
            
            switch (state) {
                case IDLE:
                case ERROR:
                    binding.buttonMic.setBackgroundColor(Color.LTGRAY);
                    break;
                case LISTENING:
                    binding.buttonMic.setBackgroundColor(Color.RED);
                    break;
                case THINKING:
                    binding.buttonMic.setBackgroundColor(Color.YELLOW);
                    break;
                case SPEAKING:
                    binding.buttonMic.setBackgroundColor(Color.GREEN);
                    break;
            }
        });

        viewModel.getChatText().observe(getViewLifecycleOwner(), text -> {
            binding.textChat.setText(text);
        });

        // マイクボタンが押されたときの処理を、権限チェックでガードする
        binding.buttonMic.setOnClickListener(v -> {
            AppState currentState = viewModel.getAppState().getValue();
            
            if (currentState == AppState.IDLE || currentState == AppState.ERROR) {
                // これから録音を開始しようとしている場合、権限があるかチェック
                if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                    viewModel.onMicButtonClicked(); // すでに許可されていればそのまま進む
                } else {
                    // 許可されていない場合は、OS標準の許可ダイアログを表示する
                    requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO);
                }
            } else {
                // 録音中や思考中など、その他の状態の時はそのままViewModelに進めさせる
                viewModel.onMicButtonClicked();
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
