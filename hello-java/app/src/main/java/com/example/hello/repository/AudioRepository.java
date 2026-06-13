package com.example.hello.repository;

import android.util.Log;
import com.example.hello.infrastructure.AudioDataListener;
import com.example.hello.infrastructure.AudioRecorderHelper;
import com.example.hello.repository.ResultListener;
import com.example.hello.infrastructure.CloudRunApiClient;
import java.util.Arrays;
import java.util.concurrent.LinkedBlockingQueue;

public class AudioRepository {
    private static final String TAG = "AudioRepository";
    
    private AudioRecorderHelper audioRecorderHelper;
    private CloudRunApiClient apiClient;
    
    // 音声データを一時保管するスレッドセーフなキュー（uITRONのデータキューに相当）
    // メモリ溢れを防ぐため、最大100個（数秒分）の容量制限を設ける
    private LinkedBlockingQueue<byte[]> audioQueue = new LinkedBlockingQueue<>(100);

    public AudioRepository() {
        audioRecorderHelper = new AudioRecorderHelper();
        apiClient = new CloudRunApiClient();
    }

    public void setIdToken(String idToken) {
        apiClient.setIdToken(idToken);
    }

    public void startRecording() {
        Log.d(TAG, "録音開始指令を受け付けました。キューをクリアします。");
        audioQueue.clear();
        
        // インターフェース（無名クラス/ラムダ式）を渡して、コールバックを登録する
        audioRecorderHelper.startRecording((data, length) -> {
            // 必要な長さだけ配列をコピーしてキューに放り込む
            byte[] validData = Arrays.copyOf(data, length);
            
            // offer() はキューが満杯の場合、ブロックせずに false を返す（音の破棄＝Drop）
            if (!audioQueue.offer(validData)) {
                Log.w(TAG, "警告: キューが満杯です！音声データが破棄されました (Backpressure)");
            }
        });
    }

    public void stopRecording() {
        audioRecorderHelper.stopRecording();
    }

    // テスト用：テキストを送信して結果を受け取る
    public void sendTestMessage(String message, final ResultListener repositoryListener) {
        // infrastructure層のコールバックを、repository層のコールバックへ変換するAdapter
        CloudRunApiClient.StreamListener infrastructureListener = new CloudRunApiClient.StreamListener() {
            @Override
            public void onChunkReceived(String text) {
                repositoryListener.onResult(text);
            }

            @Override
            public void onError(Exception e) {
                repositoryListener.onError(e);
            }

            @Override
            public void onComplete() {
                repositoryListener.onComplete();
            }
        };

        apiClient.sendTextStream(message, infrastructureListener);
    }
}
