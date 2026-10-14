package com.example.sparsearraylab

import android.os.Build
import android.util.SparseArray
import android.util.SparseIntArray
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/** 측정 블록 안에서는 박싱이 섞이지 않도록 assert와 문자열 생성을 블록 밖에 둔다. */
@RunWith(AndroidJUnit4::class)
class 메모리할당학습테스트 {
    private val n = 1_000
    private val keys = MemoryObservation.keys(n)
    private val value = "value"

    @Test
    fun `HashMap에 새 키를 넣으면 키마다 Integer와 Node가 하나씩 생긴다`() {
        val hashMap = HashMap<Int, String>()

        val allocation = AllocationCounter.measure { for (key in keys) hashMap[key] = value }

        // Integer 16 B × 1,000 + Node 24 B × 1,000 + Node[16…2048] 8개 16,448 B
        assertEquals(Allocation(2_008, 56_448), allocation)
        assertEquals(ExpectedMemory.hashMapPutNew(n), allocation)
    }

    @Test
    fun `HashMap은 같은 키 put과 get과 remove에서도 키를 박싱한다`() {
        val hashMap = HashMap<Int, String>()
        for (key in keys) hashMap[key] = value

        val putSame = AllocationCounter.measure { for (key in keys) hashMap[key] = value }
        val get = AllocationCounter.measure { for (key in keys) hashMap[key] }
        val remove = AllocationCounter.measure { for (key in keys) hashMap.remove(key) }

        assertEquals(Allocation(n, 16 * n), putSame)
        assertEquals(Allocation(n, 16 * n), get)
        assertEquals(Allocation(n, 16 * n), remove)
    }

    @Test
    fun `SparseArray는 put과 get과 remove에서 키 객체를 만들지 않는다`() {
        val sparseArray = SparseArray<String>()
        for (key in keys) sparseArray.put(key, value)

        val putSame = AllocationCounter.measure { for (key in keys) sparseArray.put(key, value) }
        val get = AllocationCounter.measure { for (key in keys) sparseArray.get(key) }
        val remove = AllocationCounter.measure { for (key in keys) sparseArray.remove(key) }

        assertEquals(Allocation.NONE, putSame)
        assertEquals(Allocation.NONE, get)
        assertEquals(Allocation.NONE, remove)
    }

    @Test
    fun `SparseArray는 꽉 찰 때만 키 배열과 값 배열을 함께 늘린다`() {
        val sparseArray = SparseArray<String>()

        val allocation = AllocationCounter.measure { for (key in keys) sparseArray.put(key, value) }

        assertEquals(ExpectedMemory.sparsePutNew(n), allocation)
    }

    @Test
    fun `SparseArray 기본 생성자는 Android 14부터 빈 배열로 시작한다`() {
        var sparseArray: SparseArray<String>? = null

        val allocation = AllocationCounter.measure { sparseArray = SparseArray() }

        val expected = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            Allocation(1, 24)              // 객체만. 배열은 모든 인스턴스가 공유하는 EmptyArray
        } else {
            Allocation(3, 24 + 56 + 56)    // 객체 + int[11] + Object[11]
        }
        assertEquals(expected, allocation)
        assertEquals(0, sparseArray!!.size())
    }

    @Test
    fun `SparseArray는 지워도 배열 용량이 줄지 않는다`() {
        val sparseArray = SparseArray<String>()
        for (key in keys) sparseArray.put(key, value)
        val deleteCount = MemoryObservation.deleteCount(n)
        for (i in 0 until deleteCount) sparseArray.remove(keys[i])
        assertEquals(n - deleteCount, sparseArray.size())  // size()가 gc()로 앞으로 당긴다

        // 지운 키를 다시 넣어도 새 배열이 필요 없다. 용량이 그대로 남아 있다는 뜻이다.
        val putBack = AllocationCounter.measure { for (i in 0 until deleteCount) sparseArray.put(keys[i], value) }

        assertEquals(Allocation.NONE, putBack)
        assertEquals(n, sparseArray.size())
    }

    @Test
    fun `HashMap Int Int는 값도 박싱하지만 SparseIntArray는 아무것도 박싱하지 않는다`() {
        val hashMap = HashMap<Int, Int>()
        val sparseIntArray = SparseIntArray()

        val hashMapPut = AllocationCounter.measure { for (key in keys) hashMap[key] = key }
        val sparseIntArrayPut = AllocationCounter.measure { for (key in keys) sparseIntArray.put(key, key) }

        assertEquals(ExpectedMemory.hashMapPutNew(n, boxedValues = true), hashMapPut)
        assertEquals(ExpectedMemory.sparsePutNew(n, intValues = true), sparseIntArrayPut)
    }

    @Test
    fun `12KB 이상인 int 배열은 Large Object Space에 페이지 단위로 잡힌다`() {
        val large = 10_000
        val largeKeys = MemoryObservation.keys(large)
        val sparseArray = SparseArray<String>()
        val sparseIntArray = SparseIntArray()

        val sparseArrayPut = AllocationCounter.measure { for (key in largeKeys) sparseArray.put(key, value) }
        val sparseIntArrayPut = AllocationCounter.measure { for (key in largeKeys) sparseIntArray.put(key, key) }

        // int[]만 페이지 단위로 커지고 Object[]는 8 B 정렬 그대로라, 둘 다 int[]인 SparseIntArray가 더 크다.
        assertEquals(ExpectedMemory.sparsePutNew(large), sparseArrayPut)
        assertEquals(ExpectedMemory.sparsePutNew(large, intValues = true), sparseIntArrayPut)
    }
}
