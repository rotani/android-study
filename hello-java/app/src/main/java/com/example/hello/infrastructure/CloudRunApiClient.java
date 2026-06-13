package com.example.hello.infrastructure;

import android.util.Log;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.concurrent.TimeUnit;
import com.example.hello.BuildConfig;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class CloudRunApiClient {
    private static final String TAG = "CloudRunApiClient";
    
    private final OkHttpClient client;
    private String idToken = null; // Googleから取得したIDトークンを保持

    // コールバック用のインターフェース（AudioDataListenerの通信版）
    public interface StreamListener {
        void onChunkReceived(String text);
        void onError(Exception e);
        void onComplete();
    }

    public CloudRunApiClient() {
        // LLMの返答は時間がかかる場合があるため、タイムアウトを長めに設定
        this.client = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(120, TimeUnit.SECONDS)
                .build();
    }

    // IDトークンをセットするメソッド
    public void setIdToken(String idToken) {
        this.idToken = idToken;
    }

    public void sendTextStream(String text, StreamListener listener) {
        // Gateway LLM の仕様に合わせたJSONを作成
        String jsonPayload = "{\"text\": \"" + text + "\"}";
        RequestBody body = RequestBody.create(jsonPayload, MediaType.get("application/json"));

        Request.Builder requestBuilder = new Request.Builder()
                .url(BuildConfig.CLOUD_RUN_URL)
                .addHeader("x-mcp-token", BuildConfig.APP_SECRET_TOKEN)
                .post(body);

        // IDトークンがあれば Authorization ヘッダーに追加する
        if (this.idToken != null) {
            requestBuilder.addHeader("Authorization", "Bearer " + this.idToken);
        }

        Request request = requestBuilder.build();

        // 非同期(裏スレッド)でリクエストを送信
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e(TAG, "通信エラー発生", e);
                listener.onError(e);
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (!response.isSuccessful() || response.body() == null) {
                    listener.onError(new IOException("サーバーエラー: " + response.code()));
                    return;
                }

                // ★ここがストリーミングのキモ！
                // 全体を一度に文字列にするのではなく、流れてくる川（InputStream）を直接読み取る
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(response.body().byteStream()))) {
                    String line;
                    // Gateway LLM は 1チャンクごとに改行(\n)を入れて NDJSON を送ってくる
                    while ((line = reader.readLine()) != null) {
                        // 1行読み取れるたびに、リアルタイムにRepositoryへ報告！
                        listener.onChunkReceived(line);
                    }
                }
                // 読み取りループを抜けたら通信完了
                listener.onComplete();
            }
        });
    }
}
