package com.example.hello.infrastructure;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;
import android.util.Base64;
import android.util.Log;

import java.util.concurrent.LinkedBlockingQueue;

public class AudioPlayerHelper {
    private static final String TAG = "AudioPlayerHelper";
    // ラズパイ(Gemini API)の設定に合わせたフォーマット (24kHz, 16bit, モノラル)
    private static final int SAMPLE_RATE = 24000;
    private static final int CHANNEL_CONFIG = AudioFormat.CHANNEL_OUT_MONO;
    private static final int AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT;

    private AudioTrack audioTrack;
    private LinkedBlockingQueue<byte[]> playbackQueue = new LinkedBlockingQueue<>();
    private Thread playbackThread;
    private volatile boolean isPlaying = false;

    public AudioPlayerHelper() {
        int minBufferSize = AudioTrack.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT);

        audioTrack = new AudioTrack.Builder()
                .setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build())
                .setAudioFormat(new AudioFormat.Builder()
                        .setEncoding(AUDIO_FORMAT)
                        .setSampleRate(SAMPLE_RATE)
                        .setChannelMask(CHANNEL_CONFIG)
                        .build())
                .setBufferSizeInBytes(minBufferSize)
                // ストリーミングで順次流し込むためのモードを指定
                .setTransferMode(AudioTrack.MODE_STREAM) 
                .build();
    }

    // Base64文字列を受け取り、デコードしてキューに入れる（プロデューサー）
    public void playBase64Audio(String base64Audio) {
        try {
            byte[] pcmData = Base64.decode(base64Audio, Base64.DEFAULT);
            playbackQueue.offer(pcmData);
            
            // データが入ってきたら、再生スレッドを起動する
            if (!isPlaying) {
                startPlayback();
            }
        } catch (IllegalArgumentException e) {
            Log.e(TAG, "Base64デコードエラー", e);
        }
    }

    // キューからデータを取り出し、スピーカーに書き込む（コンシューマー）
    private void startPlayback() {
        isPlaying = true;
        audioTrack.play();

        playbackThread = new Thread(() -> {
            Log.d(TAG, "再生スレッド開始");
            while (isPlaying) {
                try {
                    // データが来るまで待機し、来たら取り出す（take）
                    byte[] pcmData = playbackQueue.take();
                    audioTrack.write(pcmData, 0, pcmData.length);
                } catch (InterruptedException e) {
                    Log.d(TAG, "再生スレッドが中断されました");
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        });
        playbackThread.start();
    }
}
