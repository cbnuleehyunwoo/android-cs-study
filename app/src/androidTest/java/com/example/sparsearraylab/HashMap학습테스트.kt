package com.example.sparsearraylab

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HashMap학습테스트 {

    @Test
    fun `키로 값을 조회한다`() {
        val hashMap = HashMap<Int, String>()
        hashMap[7] = "first"

        assertEquals("first", hashMap[7])
    }

    @Test
    fun `같은 키에 새 값을 넣으면 기존 값을 덮어쓴다`() {
        val hashMap = HashMap<Int, String>()
        hashMap[7] = "first"

        hashMap[7] = "updated"

        assertEquals("updated", hashMap[7])
        assertEquals(1, hashMap.size)
    }

    @Test
    fun `존재하지 않는 키를 조회하면 null을 반환한다`() {
        val hashMap = HashMap<Int, String>()

        assertNull(hashMap[99])
        assertEquals("없음", hashMap[99] ?: "없음")
    }

    @Test
    fun `키에 해당하는 항목을 삭제한다`() {
        val hashMap = HashMap<Int, String>()
        hashMap[1] = "one"

        hashMap.remove(1)

        assertFalse(hashMap.containsKey(1))
        assertEquals(0, hashMap.size)
    }
}
