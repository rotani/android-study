package com.example.hello.viewmodel;

import android.util.Log;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import org.json.JSONException;
import org.json.JSONObject;
import com.example.hello.repository.AudioRepository; 
import com.example.hello.repository.ResultListener;

public class MainViewModel extends ViewModel {

    private final MutableLiveData<AppState> _appState = new MutableLiveData<>(AppState.IDLE);
    private final MutableLiveData<String> _chatText = new MutableLiveData<>("マイクボタンを押して話しかけてください");
    private final StringBuilder responseBuilder = new StringBuilder(); // 受信テキストを蓄積するための変数

    // Repositoryパターン導入
    private final AudioRepository audioRepository = new AudioRepository(); 

    public LiveData<AppState> getAppState() {
        return _appState;
    }

    public LiveData<String> getChatText() {
        return _chatText;
    }

    public void setIdToken(String idToken) {
        audioRepository.setIdToken(idToken);
    }

    public void onMicButtonClicked() {
        AppState currentState = _appState.getValue();
        
        if (currentState == AppState.IDLE || currentState == AppState.ERROR) {
            _appState.setValue(AppState.LISTENING);
            _chatText.setValue("（録音中... あなたの声を拾っています）");
            audioRepository.startRecording();; // 録音開始！
        } else if (currentState == AppState.LISTENING) {
            _appState.setValue(AppState.THINKING);
            _chatText.setValue("（思考中... Gateway LLMと通信しています）");
            responseBuilder.setLength(0); // 新しい通信の前に、前回蓄積したテキストをクリアする
            audioRepository.stopRecording(); // 録音停止！

            // ★ クラウド通信のテスト！
            audioRepository.sendTestMessage("こんにちは！", new ResultListener() {
                @Override
                public void onResult(String text) {
                    // ※通信は裏スレッドで行われるため、UIを更新するには postValue() を使う！
                    try {
                        // 文字列をJSONオブジェクトに変換
                        JSONObject jsonObject = new JSONObject(text);
                        
                        // "speech_text" というキーが存在するかチェック
                        if (jsonObject.has("speech_text")) {
                            String speechText = jsonObject.getString("speech_text");
                            Log.d("MainViewModel", "パース成功: " + speechText);
                            responseBuilder.append(speechText);
                            _chatText.postValue("受信中:\n" + responseBuilder.toString());
                        }

                    // "audio_pcm_base64" というキーが存在するかチェックし、あれば再生する
                    if (jsonObject.has("audio_pcm_base64")) {
                        String audioBase64 = jsonObject.getString("audio_pcm_base64");
                        audioRepository.playAudio(audioBase64);
                    }
                    } catch (JSONException e) {
                        // JSON形式でない、またはパースに失敗した場合は無視する（アプリが落ちないようにする）
                    }
                }

                @Override
                public void onError(Exception e) {
                    Log.e("MainViewModel", "通信エラー", e);
                    _appState.postValue(AppState.ERROR);
                    _chatText.postValue("通信エラー: " + e.getMessage());
                }

                @Override
                public void onComplete() {
                    Log.d("MainViewModel", "通信完了");
                    _appState.postValue(AppState.SPEAKING);
                    _chatText.postValue("通信完了！\n" + responseBuilder.toString()); // 最後も蓄積したテキストを残す
                }
            });
        } else if (currentState == AppState.THINKING) {
            _appState.setValue(AppState.SPEAKING);
            _chatText.setValue("こんにちは！私はあなたのパーソナルアシスタントです。");
        } else if (currentState == AppState.SPEAKING) {
            _appState.setValue(AppState.IDLE);
            _chatText.setValue("マイクボタンを押して話しかけてください");
        }
    }
}
