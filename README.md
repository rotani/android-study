# android-study

## クラス図：フレームワークとユーザーコードの関係
Android開発の基本は、「**Android OSが用意してくれているベース（親クラス）を引き継いで（継承して）、自分オリジナルの機能を追加する**」という形をとります。
今回はそこに**MVVMアーキテクチャ**が加わり、役割分担が明確になりました。

```mermaid
classDiagram
    namespace Android_Framework {
        class AppCompatActivity {
            +onCreate()
        }
        class Fragment {
            +onCreateView()
            +onViewCreated()
        }
        class ViewModel
        class LiveData
        class Observer
    }

    namespace View_Layer {
        class MainActivity
        class MainFragment
        class SecondFragment
    }
    
    namespace ViewModel_Layer {
        class MainViewModel {
            +moveToPage2()
        }
        class AppState {
            <<enumeration>>
            PAGE_1
            PAGE_2
        }
    }
    
    AppCompatActivity <|-- MainActivity : 継承 (extends)
    Fragment <|-- MainFragment : 継承 (extends)
    Fragment <|-- SecondFragment : 継承 (extends)
    ViewModel <|-- MainViewModel : 継承 (extends)
    
    MainActivity ..> MainViewModel : 監視 (observe)
    MainFragment ..> MainViewModel : 報告 (moveToPage2)
    MainViewModel o-- AppState : 状態を保持
```

### 【クラス図の解説】

- **View Layer（Activity/Fragment）**: 画面の表示とユーザー操作の受け付けのみを担当します。「次の画面が何か」という判断は行いません。
- **ViewModel Layer**: アプリの「状態（AppState）」を保持し、Viewからの報告を受けて状態を更新します。
- **単方向データフロー**: Fragment ➔ (報告) ➔ ViewModel ➔ (状態変更・通知) ➔ Activity という、矢印が一方通行で回る設計になっています。
- **継承（`<|--`）**: `MainActivity`は`AppCompatActivity`を、`MainFragment`などは`Fragment`を、`MainViewModel`は`ViewModel`を継承し、Androidフレームワークの強力な機能を利用しています。

---

## シーケンス図：MVVMとステートマシンによる画面遷移

アプリが起動し、ユーザーがボタンを押して画面が切り替わるまでの「時間の流れ」です。

```mermaid
sequenceDiagram
    participant OS as Android OS
    participant Activity as MainActivity
    participant VM as MainViewModel
    participant Fragment as MainFragment

    Note over OS, Activity: --- アプリ起動 ---
    OS->>Activity: 1. アプリ起動 (onCreate)
    activate Activity
    
    Activity->>VM: 2. ViewModelを取得
    Activity->>VM: 3. AppStateを監視 (observe)
    VM-->>Activity: 4. 初期状態(PAGE_1)を通知

    Activity->>Activity: 5. PAGE_1なのでMainFragmentをセット
    deactivate Activity
    
    Note over OS, Fragment: --- Fragmentの生成 ---
    OS->>Fragment: 6. 画面生成 (onCreateView等)
    activate Fragment
    Fragment->>Fragment: 7. ボタンにクリックイベントを登録
    deactivate Fragment

    Note over OS, Fragment: --- ユーザーが画面を操作 ---
    
    OS->>Fragment: 8. 「次の画面へ」ボタンをタップ！
    activate Fragment
    Fragment->>VM: 9. moveToPage2() を呼び出す（報告）
    deactivate Fragment
    
    activate VM
    VM->>VM: 10. AppState を PAGE_2 に更新
    VM-->>Activity: 11. 状態変化(PAGE_2)を通知 (onChanged)
    deactivate VM
    
    activate Activity
    Activity->>Activity: 12. PAGE_2なのでSecondFragmentに切り替え
    deactivate Activity
```

### 【シーケンス図の解説：MVVMの魔法】

- **主導権はAndroid OSにある（1, 6, 8）**: Androidでは「OSが必要なタイミングで、ユーザが作成したクラスのメソッド（`onCreate`やタップイベントなど）を呼び出す」という動きをします。
- **Fragmentの責務軽減 (8, 9)**: 以前はFragment自身が `SecondFragment` を呼び出していましたが、MVVMでは単に「ボタンが押されました」とViewModelに報告するだけになりました。
- **LiveDataによるリアクティブな動き (10, 11, 12)**: ViewModelの内部状態が変わると、LiveDataを通じて自動的にActivityへ通知が飛びます。Activityは「状態がPAGE_2になったから、2ページ目を出す」というリアクティブ（反応的）な動きをしています。
- **関心の分離**: これにより、「画面の見た目とタップ検知（Fragment）」「今の状態と次にどうなるかのルール（ViewModel）」「状態に合わせた画面の切り替え（Activity）」という3つの役割が綺麗に分離されました。