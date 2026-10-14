package com.example.sparsearraylab

import android.util.SparseArray
import java.util.HashMap

/** HashMap → SparseArray 비교와 UseSparseArrays 경고를 관찰하는 예제. */
object SparseArrayWarningExample {
    fun before(): HashMap<Int, String> {
        // 현재 프로젝트의 Lint에는 HashMap 검사 분기가 없다.
        val names = HashMap<Int, String>()
        names[1001] = "고양이"
        names[2001] = "강아지"
        return names
    }

    fun after(): SparseArray<String> {
        // 정수 키를 박싱하지 않는다. Map 인터페이스를 그대로 대체하지는 못한다.
        val names = SparseArray<String>()
        names.put(1001, "고양이")
        names.put(2001, "강아지")
        return names
    }

    fun boxedValuesWarning(): SparseArray<Int> {
        // 값도 정수이면 SparseIntArray 권장: UseSparseArrays 검사 대상.
        val scores = SparseArray<Int>()
        scores.put(1001, 90)
        return scores
    }
}
