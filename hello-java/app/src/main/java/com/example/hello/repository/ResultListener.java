package com.example.hello.repository;

/**
 * Repository層からApp層へ、非同期処理の結果を通知するための汎用的なコールバックインターフェース。
 * ViewModelはこのインターフェースにのみ依存し、infrastructure層の実装を知る必要がなくなる。
 */
public interface ResultListener {
    void onResult(String text);
    void onError(Exception e);
    void onComplete();
}
