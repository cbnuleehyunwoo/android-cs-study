package com.example.sparsearraylab

import android.os.Debug

/** 블록 안에서 현재 스레드가 만든 객체 수와 바이트. 바이트는 실제 할당 크기(8 B 정렬)다. */
data class Allocation(val count: Int, val bytes: Int) {
    operator fun plus(other: Allocation) = Allocation(count + other.count, bytes + other.bytes)

    companion object {
        val NONE = Allocation(0, 0)
    }
}

/**
 * ART의 스레드별 할당 카운터로 객체 수를 센다.
 * Debug의 할당 카운터 API는 deprecated지만 ART에서 동작하며, 다른 스레드의 할당은 섞이지 않는다.
 * 디버그 빌드 기준이다. 릴리스 빌드는 컴파일러 최적화로 일부 할당이 사라질 수 있다.
 */
object AllocationCounter {
    @Suppress("DEPRECATION")
    inline fun measure(block: () -> Unit): Allocation {
        Debug.resetThreadAllocCount()
        Debug.resetThreadAllocSize()
        Debug.startAllocCounting()
        block()
        Debug.stopAllocCounting()
        return Allocation(Debug.getThreadAllocCount(), Debug.getThreadAllocSize())
    }
}
