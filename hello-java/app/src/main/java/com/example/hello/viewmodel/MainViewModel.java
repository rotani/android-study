package com.example.hello.viewmodel;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

public class MainViewModel extends ViewModel {

    private final MutableLiveData<AppState> _appState = new MutableLiveData<>(AppState.IDLE);
    private final MutableLiveData<String> _chatText = new MutableLiveData<>("マイクボタンを押して話しかけてください");

    public LiveData<AppState> getAppState() {
        return _appState;
    }

    public LiveData<String> getChatText() {
        return _chatText;
    }

    public void onMicButtonClicked() {
        AppState currentState = _appState.getValue();
        
        if (currentState == AppState.IDLE || currentState == AppState.ERROR) {
            _appState.setValue(AppState.LISTENING);
            _chatText.setValue("（録音中... あなたの声を拾っています）");
        } else if (currentState == AppState.LISTENING) {
            _appState.setValue(AppState.THINKING);
            _chatText.setValue("（思考中... Gateway LLMと通信しています）");
        } else if (currentState == AppState.THINKING) {
            _appState.setValue(AppState.SPEAKING);
            _chatText.setValue("こんにちは！私はあなたのパーソナルアシスタントです。");
        } else if (currentState == AppState.SPEAKING) {
            _appState.setValue(AppState.IDLE);
            _chatText.setValue("マイクボタンを押して話しかけてください");
        }
    }
}
