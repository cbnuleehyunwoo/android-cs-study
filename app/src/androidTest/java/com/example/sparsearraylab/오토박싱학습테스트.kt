package com.example.sparsearraylab

import android.util.SparseArray
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class 오토박싱학습테스트 {

    @Test
    fun `HashMap의 Int 키는 Integer 객체로 저장된다`() {
        val hashMap = HashMap<Int, String>()
        AutoBoxingExample.putToHashMap(hashMap)

        val storedKey: Any = hashMap.keys.first()

        assertEquals(java.lang.Integer::class.java, storedKey.javaClass)
    }

    @Test
    fun `캐시 범위 밖의 키는 박싱할 때마다 다른 Integer 객체가 생긴다`() {
        val first: Any = 1042
        val second: Any = 1042

        assertEquals(first, second)
        assertNotSame(first, second)
    }

    @Test
    fun `-128부터 127까지는 캐시된 Integer를 재사용한다`() {
        val first: Any = 127
        val second: Any = 127

        assertSame(first, second)
    }

    @Test
    fun `HashMap은 조회할 때 새로 박싱한 키로도 값을 찾는다`() {
        val hashMap = HashMap<Int, String>()
        AutoBoxingExample.putToHashMap(hashMap)

        // 저장된 키와 조회용 키는 서로 다른 Integer 객체지만 equals로 찾는다.
        assertNotSame(hashMap.keys.first() as Any, 1042 as Any)
        assertEquals("A", AutoBoxingExample.getFromHashMap(hashMap))
    }

    @Test
    fun `SparseArray는 int 키를 그대로 저장한다`() {
        val sparseArray = SparseArray<String>()
        AutoBoxingExample.putToSparseArray(sparseArray)

        // keyAt은 int를 반환한다. 키 배열이 int[]이기 때문이다.
        assertEquals(1042, sparseArray.keyAt(0))
        assertEquals("A", AutoBoxingExample.getFromSparseArray(sparseArray))
    }

    @Test
    fun `nullable Int와 제네릭 List의 원소는 Integer다`() {
        val nullable: Any = AutoBoxingExample.nullableInt()!!
        val element: Any = AutoBoxingExample.genericList().first()

        assertEquals(java.lang.Integer::class.java, nullable.javaClass)
        assertEquals(java.lang.Integer::class.java, element.javaClass)
    }

    @Test
    fun `IntArray는 int 배열이다`() {
        val numbers = AutoBoxingExample.intArray()

        assertEquals(IntArray::class.java, numbers.javaClass)
        assertEquals("[I", numbers.javaClass.name)
    }
}
