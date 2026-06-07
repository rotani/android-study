package com.example.hello;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

public class MainViewModel extends ViewModel {

    // 内部でだけ書き換え可能な MutableLiveData（初期値は PAGE_1）
    private final MutableLiveData<AppState> _appState = new MutableLiveData<>(AppState.PAGE_1);

    // Modelのインスタンスを生成・保持
    private final UserModel userModel = new UserModel();

    // UIにエラーを伝えるための状態
    private final MutableLiveData<String> _errorMessage = new MutableLiveData<>();

    // 外部（Activityなど）に公開するための読み取り専用 LiveData
    public LiveData<AppState> getAppState() {
        return _appState;
    }

    public LiveData<String> getErrorMessage() {
        return _errorMessage;
    }

    public String getUserName() {
        return userModel.getName();
    }

    // Viewから受け取った入力をModelに渡し、結果に応じて状態を更新する
    public void submitName(String name) {
        if (userModel.validateAndSetName(name)) {
            _errorMessage.setValue(null); // エラーを消す
            _appState.setValue(AppState.PAGE_2); // 2ページ目へ
        } else {
            _errorMessage.setValue("名前を入力してください"); // エラーを通知
        }
    }
}
