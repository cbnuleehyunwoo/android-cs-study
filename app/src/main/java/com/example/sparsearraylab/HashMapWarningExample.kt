package com.example.sparsearraylab

import java.util.HashMap

/**
 * 정수 키 HashMap에 SparseArray 계열을 권장하던 UseSparseArrays 검사 대상.
 * 이 프로젝트의 Lint 32.4.0에는 HashMap 검사 분기가 없어 경고가 발생하지 않는다.
 */
object HashMapWarningExample {
    fun namesById(): HashMap<Int, String> {
        // 정수 키 + 객체 값: 대안은 SparseArray<String>.
        val names = HashMap<Int, String>()
        names[1001] = "고양이"
        names[2001] = "강아지"
        return names
    }

    fun scoresById(): HashMap<Int, Int> {
        // 정수 키 + 정수 값: 대안은 SparseIntArray.
        val scores = HashMap<Int, Int>()
        scores[1001] = 90
        scores[2001] = 80
        return scores
    }
}
