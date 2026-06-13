# android-study

Cloud RunのGateway LLMと通信し、MCPと連動するAndroid音声/チャットUI（Thin Client）の構築を目指す学習用プロジェクトです。

## 設計ドキュメント

- MVVMの基礎とステートマシンについて
  - ページ遷移とバリデーションを題材にした、MVVMの基本構造の図解です。（詳細は `docs/01_mvvm_basic.md` に退避・保存済み）

---

## 1. クラス図：音声・通信UIのアーキテクチャと関心の分離

```mermaid
classDiagram
    namespace Android_Framework {
        class CredentialManager {
            +getCredentialAsync()
        }
    }
    class MainFragment {
        -binding: FragmentMainBinding
        +onViewCreated()
        -signInWithGoogle()
    }
    class MainViewModel {
        -appState: MutableLiveData~AppState~
        -audioRepository: AudioRepository
        +setIdToken(idToken: String)
        +onMicButtonClicked()
    }
    class AppState {
        <<enumeration>>
        IDLE
        LISTENING
        THINKING
        SPEAKING
        ERROR
    }
    class AudioRepository {
        -audioQueue: LinkedBlockingQueue~byte[]~
        -audioRecorderHelper: AudioRecorderHelper
        -apiClient: CloudRunApiClient
        +setIdToken(idToken: String)
        +startRecording()
        +stopRecording()
        +sendTestMessage(message: String, listener: ResultListener)
    }
    class ResultListener {
        <<interface>>
        +onResult(text: String)
        +onError(e: Exception)
        +onComplete()
    }
    class AudioRecorderHelper {
        -audioRecord: AudioRecord
        -isRecording: AtomicBoolean
        +startRecording(listener: AudioDataListener)
        +stopRecording()
    }
    class AudioDataListener {
        <<interface>>
        +onAudioDataReceived(data: byte[], length: int)
    }
    class CloudRunApiClient {
        -client: OkHttpClient
        -idToken: String
        +setIdToken(idToken: String)
        +sendTextStream(text: String, listener: StreamListener)
    }
    class StreamListener {
        <<interface>>
        +onChunkReceived(text: String)
        +onError(e: Exception)
        +onComplete()
    }

    MainFragment --> CredentialManager : Googleログイン要求
    MainFragment --> MainViewModel : 監視 / 操作 (トークン引渡し)
    MainViewModel --> AppState : 状態管理
    MainViewModel --> AudioRepository : 録音/通信指示
    MainViewModel ..|> ResultListener : 実装 (無名クラス)

    AudioRepository --> AudioRecorderHelper : 委譲
    AudioRepository ..|> AudioDataListener : 実装 (ラムダ式)
    AudioRecorderHelper --> AudioDataListener : コールバック

    AudioRepository --> CloudRunApiClient : 委譲
    AudioRepository ..|> StreamListener : 実装 (Adapter)
    AudioRepository --> ResultListener : 変換して通知
    CloudRunApiClient --> StreamListener : コールバック
```

### 【クラス図の解説】
- 関心の分離: Fragment(View) / ViewModel(UIの状態) / Repository(データ) / Infrastructure(ハードウェア制御) の各層が、自身の責務にのみ集中するよう綺麗に分離されています。 
- 一方通行の依存関係: 矢印は常に左から右へ流れており、UI層がデータ層の詳細を知らない（ViewModelはAudioRepositoryしか知らない）疎結合な設計が実現できています。 
- 依存関係逆転の原則: AudioRecorderHelperはAudioRepositoryを知りません。AudioDataListenerというインターフェース（契約）を介してコールバックすることで、RepositoryがHelperに依存するのではなく、両者が抽象（インターフェース）に依存する形となり、部品の独立性が高まっています。
- **Adapterパターンによる境界防衛**: `MainViewModel` (App層) が `StreamListener` (Infrastructure層) を直接知ることを防ぐため、Repository層に `ResultListener` を新設しました。`AudioRepository` がこれを中継（Adapterとして機能）することで、層をまたぐ直接的な依存を遮断しています。
- **Androidフレームワークとの連携**: `MainFragment` はUIの描画だけでなく、OSの機能である `CredentialManager` と連携して安全にIDトークンを取得し、それをViewModelへ横流しする責務も担っています。

---

## 2. シーケンス図：録音スレッドのライフサイクルとメモリ管理

マイクボタンを押してから、裏スレッドで録音が行われ、停止時に安全にリソースが解放されるまでの流れです。

```mermaid
sequenceDiagram
    actor User
    participant UI as MainFragment
    participant VM as MainViewModel
    participant Repo as AudioRepository
    participant Audio as AudioRecorderHelper
    participant Thread as RecordingThread<br/>(裏スレッド)

    User->>UI: マイクボタンをタップ
    UI->>UI: 権限チェック (OK)
    UI->>VM: onMicButtonClicked()
    VM->>VM: 状態を LISTENING に更新
    VM->>Repo: startRecording()
    Repo->>Repo: キュー(audioQueue)をクリア
    Repo->>Audio: startRecording(listener)
    Audio->>Audio: AudioRecordの初期化
    Audio->>Thread: スレッド起動 (start)

    rect rgb(240, 240, 240)
        Note over Thread, Repo: 録音ループ (isRecording == true)
        loop 数十ミリ秒ごと
            Thread->>Thread: マイクから音声データを読み込み
            Thread->>Repo: onAudioDataReceived(data, length)
            Repo->>Repo: audioQueue.offer(data) <br/>(キューに格納 / 満杯なら破棄)
        end
    end

    User->>UI: マイクボタンを再度タップ
    UI->>VM: onMicButtonClicked()
    VM->>VM: 状態を THINKING に更新
    VM->>Repo: stopRecording()
    Repo->>Audio: stopRecording()
    Audio->>Thread: isRecording = false
    Thread->>Thread: ループを抜けてスレッド終了
    Audio->>Audio: AudioRecord解放 (release)
```

### 【シーケンス図の解説】
- **UIスレッドの保護**: `startRecording()`の呼び出しはUIスレッドで行われますが、重い録音処理は即座に裏スレッド(`RecordingThread`)に委譲されるため、画面がフリーズすることはありません。
- **コールバックとキューイング**: 裏スレッドはマイクから読み取った音声データを`onAudioDataReceived`コールバックで`Repository`に通知します。`Repository`は受け取ったデータを`LinkedBlockingQueue`に格納（キューイング）します。
- **バックプレッシャー（背圧）への対応**: `audioQueue.offer()`は、キューが満杯の時に処理をブロックせず、データを破棄して`false`を返します。これにより、将来実装する通信処理（消費者）が遅延しても、録音スレッドが詰まって音飛びしたり、アプリがクラッシュしたりするのを防ぐ安全装置として機能します。

---

## 3. シーケンス図：クラウド通信とMCP連動ストリーミング受信

マイク録音停止後、テキストデータをCloud Runに送信し、LLMが自律的にMCP（ラズパイ）経由で外部ツールを使用し、その結果をパースして画面にリアルタイム表示するまでの流れです。

```mermaid
sequenceDiagram
    actor User
    participant UI as MainFragment
    participant VM as MainViewModel
    participant Repo as AudioRepository
    participant API as CloudRunApiClient
    participant Gateway as Gateway LLM<br/>(Cloud Run)
    participant MCP as MCP Server<br/>(ラズパイ)
    participant Search as Google検索等<br/>(外部API)

    User->>UI: マイクボタンを再度タップ
    UI->>VM: onMicButtonClicked()
    VM->>VM: 状態を THINKING に更新
    VM->>Repo: stopRecording()
    VM->>Repo: sendTestMessage("こんにちは！", ResultListener)
    
    Repo->>API: sendTextStream("こんにちは！", StreamListener)
    
    Note over API, Gateway: OIDCトークン(Bearer)と合言葉を付与してリクエスト
    API->>Gateway: POST /ask-mcp
    Gateway->>Gateway: IAM認証 (トークンとAudience検証)

    rect rgb(230, 240, 255)
        Note over Gateway, Search: MCP (Model Context Protocol) 連携
        Gateway->>MCP: 1. connect & list_tools
        MCP-->>Gateway: ツール一覧 (google_search等)
        Gateway->>Gateway: LLM思考 (ユーザー意図の解釈)
        Gateway->>MCP: 2. call_tool(google_search, "明日の横浜の天気")
        MCP->>Search: 検索リクエスト
        Search-->>MCP: 検索結果
        MCP-->>Gateway: ツールの実行結果
    end
    
    Gateway->>Gateway: LLM思考 (結果を日本語に要約・音声合成)
    
    rect rgb(240, 240, 240)
        Note over API, Gateway: NDJSONによるストリーミング受信
        loop チャンク(1行のJSON)受信ごと
            Gateway-->>API: {"speech_text": "...", "audio_pcm_base64": "..."}
            API->>Repo: onChunkReceived(jsonText)
            Repo->>VM: onResult(jsonText)
            VM->>VM: JSONをパースして日本語テキストを抽出
            VM->>UI: _chatText.postValue(蓄積したテキスト)
        end
    end
    
    Gateway-->>API: (通信完了)
    Repo->>VM: onComplete() / onError(e)
    VM->>VM: 状態を SPEAKING または ERROR に更新
    VM->>UI: LiveData経由で画面更新
```

### 【シーケンス図の解説】 
- MCPによる自律的な機能拡張: Androidアプリからは単に「こんにちは！」というテキストを投げただけですが、Gateway LLMが自律的に「天気を調べる必要がある」と判断し、ラズパイのMCPサーバーへツールの実行を依頼しています。Android側は外部APIの存在を一切意識せず、要約された結果だけを受け取ることができます。 
- JSONのパースとUIの蓄積: Gateway LLMからは文字列だけでなく音声の生データ（Base64）を含んだ巨大なJSONが送られてきます。ViewModelは JSONObject を用いて必要な speech_text だけを抽出し、StringBuilder で過去のテキストに継ぎ足しながら画面を更新します。 

---

## 4. シーケンス図：Google Sign-In と Cloud Run IAM認証 (OAuth 2.0 / OIDC)

アプリ起動時に OAuth 2.0 / OIDC (OpenID Connect) ベースの Google 認証を行い、取得したIDトークンを用いてセキュアに Cloud Run (IAM) へアクセスするまでの一連の流れです。

```mermaid
sequenceDiagram
    actor User
    participant App as Androidアプリ<br/>(MainFragment)
    participant CM as Credential Manager<br/>(Android OS)
    participant Google as Google 認証サーバー
    participant CloudRun as Cloud Run<br/>(IAM / カスタムオーディエンス)

    Note over User, Google: --- アプリ起動時: Google Sign-In ---
    App->>CM: getCredentialAsync()<br/>(Web用クライアントID指定, AutoSelect=true)
    
    alt 初回ログイン時 (またはAutoSelect失敗時)
        CM-->>User: アカウント選択ダイアログ表示
        User->>CM: アカウントを選択して「続行」
    end

    Note over CM, Google: 【重要】アプリの身元証明とAudience(宛先)の指定
    CM->>Google: 認証リクエスト<br/>・身元: Android用クライアントID (裏側で自動付与)<br/>・宛先: Web用クライアントID
    Google->>Google: アプリの「SHA-1」と「パッケージ名」が<br/>GCPのAndroid用ID登録と一致するか検証
    Google-->>CM: 検証成功: IDトークン(JWT)を発行<br/>(※2回目以降は画面を出さずに即座にここまで進む)

    CM-->>App: GoogleIdTokenCredential (IDトークン)
    App->>App: 通信クライアントにトークンを保持

    Note over App, CloudRun: --- 通信時: IAM認証によるアクセス制御 ---
    App->>CloudRun: リクエスト送信<br/>Authorization: Bearer <ID Token>
    CloudRun->>CloudRun: 1. トークンのAudienceが<br/>「Web用クライアントID」と一致するか検証
    CloudRun->>CloudRun: 2. 送信元Googleアカウントが<br/>「Cloud Run 起動元」権限を持つか検証
    CloudRun-->>App: 認証成功 (API処理へ進む)
```
### 【シーケンス図の解説】 
- 2つのOAuthクライアントIDの使い分け（最重要）: AndroidアプリからCloud Runへアクセスするには、GCP上で「Android用」と「Web用」の2つのクライアントIDを発行する必要があります。
  - **Android用クライアントID**: アプリの正当性を証明する名札。Googleの認証サーバーは、通信元のアプリの `SHA-1` と `パッケージ名` がこのIDの設定と一致しているかを検証し、偽装アプリからのアクセスを弾きます。
  - **Web用クライアントID**: トークンの宛先（Audience）。Androidアプリは「私の身元はAndroid用IDで証明しますが、トークンはあのWebサーバー（Web用ID）宛てに発行してください」と要求します。 
- **OIDCとIAMによる強固なバックエンド**: Cloud RunのIAMは、受け取ったトークンが「正しい宛先（Web用ID）に向けて発行されたか」と「リクエスト元のGmailアドレスが許可されているか」を厳格にチェックします。これにより、URLやアプリのソースコードが流出しても第三者は絶対にアクセスできないセキュアな基盤が実現されています。
- **AutoSelectEnabledによるUX向上**: `CredentialManager` の設定により、一度ログインしてOSとの信頼関係が確立されると、2回目以降は「続行」ボタンを押す手間すら省かれ、バックグラウンドで透過的（自動的）にこれらの認証・トークン発行処理が行われます。