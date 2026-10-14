package com.example.sparsearraylab

import android.os.Build
import android.system.Os
import android.system.OsConstants

/**
 * visuals/sparsearray-summary.md 4장의 계산식.
 * ART 기준: 객체 헤더 8 B, 참조 4 B, 배열 헤더 12 B, 8 B 정렬.
 */
object ExpectedMemory {
    const val HASH_MAP = 40
    const val NODE = 24
    const val INTEGER = 16
    // 헤더 8 + mGarbage 1 + mKeys 4 + mValues 4 + mSize 4 = 21 → 24
    const val SPARSE_ARRAY = 24
    // 헤더 8 + mKeys 4 + mValues 4 + mSize 4 = 20 → 24
    const val SPARSE_INT_ARRAY = 24

    /** 이 크기 이상인 원시 타입 배열은 Large Object Space에 페이지 단위로 잡힌다. Object[]는 해당하지 않는다. */
    const val LARGE_OBJECT_THRESHOLD = 12 * 1024

    val pageSize: Int by lazy { Os.sysconf(OsConstants._SC_PAGESIZE).toInt() }

    fun align8(bytes: Int) = (bytes + 7) / 8 * 8

    private fun roundUp(bytes: Int, unit: Int) = (bytes + unit - 1) / unit * unit

    /** int[], Object[], Node[]는 원소가 모두 4 B다. primitive는 int[]처럼 원시 타입 배열인지다. */
    fun arrayBytes(length: Int, primitive: Boolean = false): Int {
        val raw = 12 + 4 * length
        return if (primitive && raw >= LARGE_OBJECT_THRESHOLD) roundUp(raw, pageSize) else align8(raw)
    }

    /** HashMap()에 n개를 넣는 동안 만드는 테이블 용량. 첫 put에 16, size > 용량 × 0.75면 두 배. */
    fun hashMapTables(n: Int): List<Int> {
        if (n == 0) return emptyList()
        val tables = mutableListOf(16)
        while (n > tables.last() * 3 / 4) tables += tables.last() * 2
        return tables
    }

    /**
     * VMRuntime.newUnpaddedArray: 요청 길이로 잡힌 공간을 남김없이 채운다.
     * 보통은 8 B 정렬까지라 8 → 9, 18 → 19. 큰 int[]는 페이지 끝까지라 훨씬 길어진다.
     */
    fun unpaddedLength(minLength: Int, primitive: Boolean) = (arrayBytes(minLength, primitive) - 12) / 4

    /** GrowingArrayUtils.growSize */
    fun growSize(size: Int) = if (size <= 4) 8 else size * 2

    /**
     * SparseArray(), SparseIntArray()의 시작 길이.
     * Android 14(API 34)부터 this(0)으로 빈 배열을 공유하고, 그 전에는 this(10)이라 unpadded 11칸이다.
     */
    val defaultStartLength: Int
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) 0 else unpaddedLength(10, false)

    /** 기본 생성자가 만드는 것. 14 이상은 객체 하나, 그 전에는 객체와 11칸 배열 두 개. */
    val sparseConstructor: Allocation
        get() = if (defaultStartLength == 0) Allocation(1, SPARSE_ARRAY)
        else Allocation(3, SPARSE_ARRAY + 2 * arrayBytes(defaultStartLength))

    /**
     * 오름차순으로 n개를 넣는 동안 배열 하나가 새로 만들어지는 길이.
     * 키 배열과 값 배열은 GrowingArrayUtils.insert가 각자 자기 길이를 보고 늘리므로 따로 계산한다.
     */
    fun arrayGrowth(n: Int, primitive: Boolean): List<Int> {
        var length = defaultStartLength
        val lengths = mutableListOf<Int>()
        for (size in 0 until n) {
            if (size >= length) {
                length = unpaddedLength(growSize(size), primitive)
                lengths += length
            }
        }
        return lengths
    }

    private fun lastLength(n: Int, primitive: Boolean) = arrayGrowth(n, primitive).lastOrNull() ?: defaultStartLength

    /** HashMap()에 새 키 n개 put: 키 Integer, Node, 확장할 때마다 테이블. 값도 Int면 값 Integer까지. */
    fun hashMapPutNew(n: Int, boxedValues: Boolean = false): Allocation {
        val tables = hashMapTables(n)
        val perEntry = NODE + INTEGER + if (boxedValues) INTEGER else 0
        val objectsPerEntry = if (boxedValues) 3 else 2
        return Allocation(objectsPerEntry * n + tables.size, perEntry * n + tables.sumOf { arrayBytes(it) })
    }

    /** 같은 키 put, get, remove: 키를 박싱한 Integer만 */
    fun boxedKeys(n: Int) = Allocation(n, INTEGER * n)

    /** SparseArray(intValues = false)·SparseIntArray(intValues = true)에 오름차순 n개 put: 꽉 찬 배열만 새로 */
    fun sparsePutNew(n: Int, intValues: Boolean = false): Allocation {
        val keys = arrayGrowth(n, primitive = true)
        val values = arrayGrowth(n, primitive = intValues)
        return Allocation(keys.size + values.size,
            keys.sumOf { arrayBytes(it, primitive = true) } + values.sumOf { arrayBytes(it, intValues) })
    }

    /** 살아 있는 크기. tableFor개를 넣었던 테이블이 그대로 남은 채 n개가 들어 있다. */
    fun hashMapRetained(n: Int, boxedValues: Boolean = false, tableFor: Int = n): Int {
        val table = hashMapTables(tableFor).lastOrNull()?.let { arrayBytes(it) } ?: 0
        return HASH_MAP + table + n * (NODE + INTEGER + if (boxedValues) INTEGER else 0)
    }

    /** 살아 있는 크기. 지워도 배열은 줄지 않으므로 넣었던 개수(capacityFor)로만 정해진다. */
    fun sparseRetained(capacityFor: Int, intValues: Boolean = false): Int {
        val keys = lastLength(capacityFor, primitive = true)
        val values = lastLength(capacityFor, primitive = intValues)
        // 길이 0이면 모든 인스턴스가 공유하는 EmptyArray라 이 객체 몫이 아니다.
        val arrays = if (keys == 0) 0 else arrayBytes(keys, primitive = true) + arrayBytes(values, intValues)
        return (if (intValues) SPARSE_INT_ARRAY else SPARSE_ARRAY) + arrays
    }
}
