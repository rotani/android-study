package com.example.hello;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

public class MainViewModel extends ViewModel {

    // 内部でだけ書き換え可能な MutableLiveData（初期値は PAGE_1）
    private final MutableLiveData<AppState> _appState = new MutableLiveData<>(AppState.PAGE_1);

    // 外部（Activityなど）に公開するための読み取り専用 LiveData
    public LiveData<AppState> getAppState() {
        return _appState;
    }

    // ボタンが押されたときなどに、状態を PAGE_2 に進めるメソッド
    public void moveToPage2() {
        _appState.setValue(AppState.PAGE_2);
    }
}
