package com.example.sparsearraylab

import android.util.SparseArray
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SparseArray학습테스트 {

    @Test
    fun `희소 배열은 put에 키 값을 넣어 키값 항목을 추가할 수 있다`() {
        val sparseArray = SparseArray<String>()
        sparseArray.put(1, "one")

        assertEquals("one", sparseArray.get(1))
    }

    @Test
    fun `희소 배열은 get에 키를 넣어 값 항목을 가져올 수 있다`() {
        val sparseArray = SparseArray<String>()
        sparseArray.put(1, "one")

        assertEquals("one", sparseArray.get(1))
    }

    @Test
    fun `희소 배열은 remove에 키를 넣어 값 항목을 삭제할 수 있다`() {
        val sparseArray = SparseArray<String>()
        sparseArray.put(1, "one")

        sparseArray.remove(1)

        assertFalse(sparseArray.contains(1))
        assertNull(sparseArray.get(1))
    }

    @Test
    fun `희소 배열의 값은 키로 조회된다`() {
        val sparseArray = SparseArray<String>()
        sparseArray.put(7, "first")

        assertEquals("first", sparseArray.get(7))
    }

    @Test
    fun `희소 배열은 음수 키가 가능하다`() {
        val sparseArray = SparseArray<String>()
        sparseArray.put(-1, "음수 키에 저장된 값")

        assertEquals("음수 키에 저장된 값", sparseArray.get(-1))

    }

    @Test
    fun `희소 배열은 Int 키만 가능하다`() {
        val putKeyTypes = SparseArray::class.java.methods
            .filter { it.name == "put" }
            .map { it.parameterTypes.first() }

        // put 은 하나뿐이고, 키 자리는 박싱되지 않는 원시 int 다
        assertEquals(listOf(Int::class.javaPrimitiveType), putKeyTypes)
    }

    @Test
    fun `희소 배열의 값은 어떤 타입이든 가능하다`() {
        val putValueTypes = SparseArray::class.java.methods
            .filter { it.name == "put" }
            .map { it.parameterTypes.last() }

        assertEquals(listOf(Any::class.java), putValueTypes)
    }

    @Test
    fun `희소 배열은 같은 키에 새 값을 할당할 수 있고 기존 값은 덮어씌워진다`() {
        val sparseArray = SparseArray<String>()
        sparseArray.put(7, "first")

        sparseArray.put(7, "updated")

        assertEquals("updated", sparseArray.get(7))
        assertEquals(1, sparseArray.size())
    }

    @Test
    fun `희소 배열에서 존재하지 않는 키로 값을 조회하면 null을 반환한다`() {
        val sparseArray = SparseArray<String>()

        assertNull(sparseArray.get(999))
    }

    @Test
    fun `희소 배열에서 get에 키와 함께 기본값을 전달하면 키가 존재하지 않을 때 기본값을 반환한다`() {
        val sparseArray = SparseArray<String>()

        assertEquals("기본값", sparseArray.get(99, "기본값"))
    }

    @Test
    fun `희소 배열은 키를 오름차순으로 순회한다`() {
        val sparseArray = SparseArray<String>()
        listOf(30, 10, 20).forEach { key -> sparseArray.put(key, "value-$key") }

        val keys = (0 until sparseArray.size()).map(sparseArray::keyAt)

        assertEquals(listOf(10, 20, 30), keys)
    }

    @Test
    fun `키의 위치는 정렬된 키 배열을 이진 탐색한 결과와 같다`() {
        val sortedKeys = intArrayOf(10, 20, 30, 40)
        val sparseArray = SparseArray<String>()
        listOf(40, 10, 30, 20).forEach { key -> sparseArray.put(key, "value-$key") }

        // 정렬된 키: [10, 20, 30, 40]
        listOf(5, 10, 25, 30, 45).forEach { key ->
            assertEquals(sortedKeys.binarySearch(key), sparseArray.indexOfKey(key))
        }
    }
}
