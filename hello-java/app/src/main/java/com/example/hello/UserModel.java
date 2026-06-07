package com.example.hello;

public class UserModel {
    // アプリ実行中のみメモリ上に保持されるデータ
    private String name = "";

    // ビジネスロジック（文字が空じゃないかチェックして保存）
    public boolean validateAndSetName(String input) {
        if (input == null || input.trim().isEmpty()) {
            return false; // NG
        }
        this.name = input.trim();
        return true; // OK
    }

    public String getName() {
        return name;
    }
}
