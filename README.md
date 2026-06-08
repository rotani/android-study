# android-study

Cloud RunのGateway LLMと通信し、MCPと連動するAndroid音声/チャットUI（Thin Client）の構築を目指す学習用プロジェクトです。

## 設計ドキュメント

- MVVMの基礎とステートマシンについて
  - ページ遷移とバリデーションを題材にした、MVVMの基本構造の図解です。（詳細は `docs/01_mvvm_basic.md` に退避・保存済み）

---

## 1. クラス図：音声UIのアーキテクチャと関心の分離

```mermaid
classDiagram
    namespace Android_Framework {
        class AudioRecord {
            +read()
            +release()
        }
        class Thread {
            +start()
        }
    }

    namespace UI_Layer {
        class MainActivity
        class MainFragment
    }

    namespace ViewModel_Layer {
        class MainViewModel {
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
    }

    namespace Infrastructure_Layer {
        class AudioRecorderHelper {
            -AtomicBoolean isRecording
            +startRecording()
            +stopRecording()
        }
    }
    
    MainActivity ..> MainFragment : 初期表示
    MainFragment ..> MainViewModel : 監視 (observe) / 操作
    MainViewModel o-- AppState : 状態を保持
    MainViewModel o-- AudioRecorderHelper : 録音処理を委譲
    AudioRecorderHelper *-- AudioRecord : 包含 (マイク制御)
    AudioRecorderHelper *-- Thread : 包含 (非同期処理)
```

### 【クラス図の解説】
- **UI Layer**: 画面の描画と、Android OS特有の「マイク権限（パーミッション）の要求」のみを担当します。録音の実処理は持ちません。
- **ViewModel Layer**: アプリの状態（`IDLE`, `LISTENING`など）を管理し、UIからのボタンタップに応じて状態を遷移させます。
- **Infrastructure Layer**: 外部リソース（今回はハードウェアのマイク）にアクセスする専門のクラスです。将来的にRepositoryパターンを導入し、ViewModelから直接触れないように隠蔽する予定です。

---

## 2. シーケンス図：録音スレッドのライフサイクルとメモリ管理

マイクボタンを押してから、裏スレッドで録音が行われ、停止時に安全にリソースが解放されるまでの流れです。

```mermaid
sequenceDiagram
    participant User
    participant View as MainFragment
    participant VM as MainViewModel
    participant Audio as AudioRecorderHelper
    participant Thread as 録音スレッド
    participant OS as Android OS (Mic)

    Note over View, Audio: --- 録音開始 (IDLE ➔ LISTENING) ---
    User->>View: 1. マイクボタンをタップ
    View->>View: 2. パーミッション(権限)チェック
    View->>VM: 3. onMicButtonClicked()
    activate VM
    VM->>VM: 4. 状態を LISTENING に更新
    VM->>Audio: 5. startRecording()
    activate Audio
    Audio->>OS: 6. マイクの初期化・占有
    Audio->>Thread: 7. new Thread().start() (別スレッド起動)
    activate Thread
    Audio-->>VM: 
    deactivate Audio
    VM-->>View: 8. 状態変化通知 (ボタンを赤色に)
    deactivate VM

    Note over Thread, OS: --- 録音中（非同期ループ） ---
    loop isRecording が true の間
        Thread->>OS: 9. audioRecord.read()
        OS-->>Thread: 10. 音声データ(PCM) 640bytes
        Thread->>Thread: 11. 同じバッファを上書き（GC回避）
    end

    Note over View, OS: --- 録音停止 (LISTENING ➔ THINKING) ---
    User->>View: 12. マイクボタンを再度タップ
    View->>VM: 13. onMicButtonClicked()
    activate VM
    VM->>VM: 14. 状態を THINKING に更新
    VM->>Audio: 15. stopRecording()
    activate Audio
    Audio->>Audio: 16. isRecording = false (フラグを折る)
    Audio->>OS: 17. audioRecord.release() (マイク解放)
    Audio-->>VM: 
    deactivate Audio
    VM-->>View: 18. 状態変化通知 (ボタンを黄色に)
    deactivate VM

    Note over Thread, OS: --- スレッドの自然死 ---
    Thread->>Thread: 19. whileループを抜け、処理終了
    destroy Thread
    Note right of Thread: 20. スレッドが破棄され、メモリが回収される
```

### 【シーケンス図の解説】
- **非同期処理 (7〜11)**: 録音という重い処理をUIスレッドとは別の裏スレッド（`recordingThread`）で行うことで、画面のフリーズ（ANR）を防いでいます。
- **バッファの使い回し (11)**: 音声データを受け取る配列（バッファ）をループ内で毎回 `new` するのではなく、同じ配列を上書きし続けることで、GC（ガベージコレクション）によるプチフリーズ（音飛び）を回避しています。
- **安全な終了と解放 (16〜20)**: `stopRecording()` でループフラグを折ることでスレッドを自然終了させます。同時に `release()` を呼ぶことで、他アプリがマイクを使えなくなる不具合を確実に防いでいます。