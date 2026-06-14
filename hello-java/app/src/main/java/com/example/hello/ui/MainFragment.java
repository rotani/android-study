package com.example.hello.ui;

import android.os.Bundle;
import android.Manifest;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.graphics.Color;
import android.widget.Toast;
import android.util.Log;
import android.text.method.ScrollingMovementMethod;
import android.os.CancellationSignal;
import java.util.concurrent.Executors;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.credentials.CredentialManager;
import androidx.credentials.CredentialManagerCallback;
import androidx.credentials.GetCredentialRequest;
import androidx.credentials.GetCredentialResponse;
import androidx.credentials.exceptions.GetCredentialException;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import com.google.android.libraries.identity.googleid.GetGoogleIdOption;
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential;
import com.example.hello.databinding.FragmentMainBinding;
import com.example.hello.BuildConfig;
import com.example.hello.viewmodel.MainViewModel;
import com.example.hello.viewmodel.AppState;

public class MainFragment extends Fragment {

    // View Bindingのインスタンスを保持する変数
    private FragmentMainBinding binding;

    // 権限要求の結果を受け取るランチャー
    private ActivityResultLauncher<String> requestPermissionLauncher;

    // 連打防止用のタイムスタンプと、権限要求中を示すフラグ
    private long lastClickTime = 0;
    private boolean isRequestingPermission = false;
    private long lastTokenFetchTime = 0; // トークン取得の最終時刻を保持

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // 権限を要求した結果（許可されたか、拒否されたか）を受け取るコールバックを登録
        requestPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                isGranted -> {
                    isRequestingPermission = false; // OSから返答が来たのでロックを解除
                    if (isGranted) {
                        // 許可されたら、ViewModelにマイクボタンが押されたことを伝える（録音開始）
                        MainViewModel viewModel = new ViewModelProvider(requireActivity()).get(MainViewModel.class);
                        viewModel.onMicButtonClicked();
                    } else {
                        // 拒否された場合、OSの仕様で「今後ダイアログを表示しない」状態になっているかチェック
                        if (!shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO)) {
                            // ダイアログが出なくなっているため、設定画面へ直接誘導する親切なダイアログを出す
                            new AlertDialog.Builder(requireContext())
                                    .setTitle("マイク権限が必要です")
                                    .setMessage("音声入力を利用するには、アプリの設定画面からマイクの権限を「許可」に変更してください。")
                                    .setPositiveButton("設定を開く", (dialog, which) -> {
                                        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                                        Uri uri = Uri.fromParts("package", requireActivity().getPackageName(), null);
                                        intent.setData(uri);
                                        startActivity(intent);
                                    })
                                    .setNegativeButton("キャンセル", null)
                                    .show();
                        } else {
                            // まだダイアログが出る余地がある場合は、トーストで警告のみ
                            Toast.makeText(requireContext(), "マイクの権限が許可されないと録音できません", Toast.LENGTH_SHORT).show();
                        }
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

        // チャットテキストが長くなった場合に指でスクロールできるようにする
        binding.textChat.setMovementMethod(new ScrollingMovementMethod());

        viewModel.getChatText().observe(getViewLifecycleOwner(), text -> {
            binding.textChat.setText(text);
        });

        // マイクボタンが押されたときの処理を、権限チェックでガードする
        binding.buttonMic.setOnClickListener(v -> {
            // 1. 連打防止（デバウンス処理）：500ミリ秒以内の連続タップは完全に無視する
            long currentTime = System.currentTimeMillis();
            if (currentTime - lastClickTime < 500) {
                return;
            }
            lastClickTime = currentTime;

            // 2. 権限要求中のロック：ダイアログが出ている最中はタップを無視する
            if (isRequestingPermission) {
                return;
            }

            AppState currentState = viewModel.getAppState().getValue();
            
            if (currentState == AppState.IDLE || currentState == AppState.ERROR) {
                // ★ トークン取得から50分(3000000ミリ秒)以上経過している場合のみ更新する
                // これにより、毎回「ログイン...」がチラ見えするのを防ぎつつ、期限切れを回避する
                if (System.currentTimeMillis() - lastTokenFetchTime > 50 * 60 * 1000) {
                    signInWithGoogle();
                }

                // これから録音を開始しようとしている場合、権限があるかチェック
                if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                    viewModel.onMicButtonClicked(); // すでに許可されていればそのまま進む
                } else {
                    isRequestingPermission = true; // 権限要求を開始したのでロックする
                    // 許可されていない場合は、OS標準の許可ダイアログを表示する
                    requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO);
                }
            } else {
                // 録音中や思考中など、その他の状態の時はそのままViewModelに進めさせる
                viewModel.onMicButtonClicked();
            }
        });

        // アプリ起動（画面表示）時にGoogleサインインを実行
        signInWithGoogle();
    }

    // Googleサインインを実行し、IDトークンを取得するメソッド
    private void signInWithGoogle() {
        CredentialManager credentialManager = CredentialManager.create(requireContext());

        // ★ここに「Web アプリケーション用」のクライアントIDを指定します（Android用ではありません！）
        // trim() を使って、見えない空白や改行コード(\r\n)を完全に削ぎ落とす
        String serverClientId = BuildConfig.WEB_CLIENT_ID.trim();
        
        Log.d("Auth", "Client ID Length: " + serverClientId.length() + ", Value: [" + serverClientId + "]");

        GetGoogleIdOption googleIdOption = new GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(false)
                .setServerClientId(serverClientId)
                .setAutoSelectEnabled(true)
                .build();

        GetCredentialRequest request = new GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build();

        credentialManager.getCredentialAsync(
                requireContext(),
                request,
                new CancellationSignal(),
                Executors.newSingleThreadExecutor(),
                new CredentialManagerCallback<GetCredentialResponse, GetCredentialException>() {
                    @Override
                    public void onResult(GetCredentialResponse result) {
                        try {
                            GoogleIdTokenCredential credential = GoogleIdTokenCredential.createFrom(result.getCredential().getData());
                            String idToken = credential.getIdToken();
                            // 取得成功！Logcatでトークンが取得できたか確認します
                            Log.d("Auth", "Google Login Success! ID Token: " + idToken);
                            lastTokenFetchTime = System.currentTimeMillis(); // 取得成功時刻を記録
                            
                            // ViewModelに取得したトークンを渡す
                            MainViewModel viewModel = new ViewModelProvider(requireActivity()).get(MainViewModel.class);
                            viewModel.setIdToken(idToken);
                        } catch (Exception e) {
                            Log.e("Auth", "GoogleIdTokenCredential のパースエラー", e);
                        }
                    }

                    @Override
                    public void onError(GetCredentialException e) {
                        Log.e("Auth", "サインイン失敗", e);
                    }
                }
        );
    }

    // Fragmentのビューが破棄される時にbindingを空にする（メモリリーク対策の鉄則）
    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
