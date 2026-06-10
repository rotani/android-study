package com.example.hello.infrastructure;

/**
 * マイクから取得した音声データを受け取るためのコールバックインターフェース。
 * C言語における「関数ポインタの登録」と同じ役割を果たします。
 */
public interface AudioDataListener {
    // 音声データ（PCMバイナリ）と、実際に読み込めたバイト数を渡す
    void onAudioDataReceived(byte[] data, int length);
}
