package com.example.hello.infrastructure;

import android.annotation.SuppressLint;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.util.Log;

import java.util.concurrent.atomic.AtomicBoolean;
import com.example.hello.infrastructure.AudioDataListener;

public class AudioRecorderHelper {
    private static final String TAG = "AudioRecorderHelper";
    
    // LLM(Gemini等)の音声認識で推奨される標準的な設定（16kHz, モノラル, 16bit）
    private static final int SAMPLE_RATE = 16000;
    private static final int CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO;
    private static final int AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT;

    private AudioRecord audioRecord;
    private Thread recordingThread;
    
    // スレッド間（UIスレッドと録音スレッド）で安全にフラグをやり取りするための型
    private final AtomicBoolean isRecording = new AtomicBoolean(false);

    // 「権限チェックはFragment側で既にやっているから警告を出さないで」というおまじない
    @SuppressLint("MissingPermission")
    public void startRecording(AudioDataListener listener) {
        if (isRecording.get()) return;

        // この設定で録音するために必要な最小のバッファ（メモリの受け皿）のサイズをOSに計算させる
        int bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT);
        if (bufferSize == AudioRecord.ERROR || bufferSize == AudioRecord.ERROR_BAD_VALUE) {
            Log.e(TAG, "無効なバッファサイズです");
            return;
        }

        // マイク(MIC)から生の音声を拾うインスタンスを生成
        audioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC, SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT, bufferSize);
        if (audioRecord.getState() != AudioRecord.STATE_INITIALIZED) {
            Log.e(TAG, "AudioRecordの初期化に失敗しました");
            return;
        }

        audioRecord.startRecording();
        isRecording.set(true);

        // 録音処理は重いため、画面がフリーズしないように別の裏スレッド（非同期）で回し続ける
        recordingThread = new Thread(() -> {
            byte[] audioBuffer = new byte[bufferSize];
            Log.d(TAG, "🔴 録音スレッド開始！");
            while (isRecording.get()) {
                int readResult = audioRecord.read(audioBuffer, 0, audioBuffer.length);
                if (readResult > 0) {
                    // ここで実際のPCMバイナリデータ(audioBuffer)が取得できている！
                     // ★コールバック関数を呼び出して、録音データを外（Repository）へ渡す
                    if (listener != null) {
                        listener.onAudioDataReceived(audioBuffer, readResult);
                    }
                    // 今回は通信しないため、データが取れ続けていることだけをログに出力する
                    Log.d(TAG, "🎙️ 音声データを読み込みました: " + readResult + " bytes");
                }
            }
            Log.d(TAG, "⏹️ 録音スレッド終了");
        });
        recordingThread.start();
    }

    public void stopRecording() {
        if (!isRecording.get()) return;
        isRecording.set(false); // ループを抜けるフラグを立てる

        audioRecord.stop();
        audioRecord.release(); // OSにマイクの主導権を返す
        audioRecord = null;
    }
}
