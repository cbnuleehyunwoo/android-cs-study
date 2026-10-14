package com.example.sparsearraylab

import android.util.SparseArray
import android.util.SparseIntArray

/** 한 작업의 측정값과 계산식 기대값. retainedBytes는 작업 후 컨테이너가 붙잡고 있는 크기의 기대값이다. */
data class ObservationRow(
    val label: String,
    val measured: Allocation,
    val expected: Allocation,
    val retainedBytes: Int? = null,
) {
    val matches get() = measured == expected
}

/**
 * 힙 덤프에서 찾기 쉽도록 붙잡아 둔 컨테이너를 한 객체에 모은다.
 * Profiler의 Analyze Memory Usage에서 HeldContainers를 검색해 각 필드의 Retained Size를 본다.
 */
object HeldContainers {
    var hashMap: HashMap<Int, String>? = null
    var sparseArray: SparseArray<String>? = null
    var intHashMap: HashMap<Int, Int>? = null
    var sparseIntArray: SparseIntArray? = null
    var keys = IntArray(0)
    var deleted = false

    val isHeld get() = hashMap != null

    fun clear() {
        hashMap = null
        sparseArray = null
        intHashMap = null
        sparseIntArray = null
        keys = IntArray(0)
        deleted = false
    }
}

/**
 * 화면의 세 관찰을 실행하고 측정값과 기대값을 돌려준다.
 * 단계마다 함수를 나눠 Profiler 콜스택에서 어느 단계의 할당인지 구분되게 한다.
 */
object MemoryObservation {
    // 값은 모든 컨테이너가 같은 String 객체를 공유한다. 그래서 어느 컨테이너의 Retained에도 들어가지 않는다.
    private const val VALUE = "value"

    /** Integer 캐시(-128~127) 밖의 키. 박싱할 때마다 새 Integer가 생긴다. */
    fun keys(n: Int) = IntArray(n) { 1_000 + it }

    fun deleteCount(n: Int) = n * 9 / 10

    private var warmedUp = false

    /**
     * 프로세스에서 처음 실행하는 경로는 측정과 무관한 일회성 할당(8 B 객체 1개 등)이 섞인다.
     * 측정하기 전에 같은 경로를 버릴 컨테이너로 한 번씩 실행해 둔다.
     */
    private fun warmUp() {
        if (warmedUp) return
        val keys = keys(20)
        boxingRows(keys)
        holdRows(keys)
        deleteRows(keys)
        HeldContainers.clear()
        warmedUp = true
    }

    fun boxing(n: Int): List<ObservationRow> {
        warmUp()
        return boxingRows(keys(n))
    }

    private fun boxingRows(keys: IntArray): List<ObservationRow> {
        val n = keys.size
        val hashMap = HashMap<Int, String>()
        val sparseArray = SparseArray<String>()
        return listOf(
            ObservationRow("HashMap 새 키 put", AllocationCounter.measure { hashMapPutNewKeys(hashMap, keys) },
                ExpectedMemory.hashMapPutNew(n)),
            ObservationRow("HashMap 같은 키 put", AllocationCounter.measure { hashMapPutSameKeys(hashMap, keys) },
                ExpectedMemory.boxedKeys(n)),
            ObservationRow("HashMap get", AllocationCounter.measure { hashMapGetKeys(hashMap, keys) },
                ExpectedMemory.boxedKeys(n)),
            ObservationRow("SparseArray 새 키 put", AllocationCounter.measure { sparseArrayPutNewKeys(sparseArray, keys) },
                ExpectedMemory.sparsePutNew(n)),
            ObservationRow("SparseArray 같은 키 put", AllocationCounter.measure { sparseArrayPutSameKeys(sparseArray, keys) },
                Allocation.NONE),
            ObservationRow("SparseArray get", AllocationCounter.measure { sparseArrayGetKeys(sparseArray, keys) },
                Allocation.NONE),
        )
    }

    // 기대: Integer n + HashMap$Node n + 확장할 때마다 Node[]
    private fun hashMapPutNewKeys(hashMap: HashMap<Int, String>, keys: IntArray) {
        for (key in keys) hashMap[key] = VALUE
    }

    // 기대: Integer n. 같은 키라 Node는 만들지 않고 값만 바꾼다.
    private fun hashMapPutSameKeys(hashMap: HashMap<Int, String>, keys: IntArray) {
        for (key in keys) hashMap[key] = VALUE
    }

    // 기대: Integer n. 조회용 키도 박싱한다.
    private fun hashMapGetKeys(hashMap: HashMap<Int, String>, keys: IntArray) {
        for (key in keys) checkNotNull(hashMap[key])
    }

    // 기대: Integer 0. 배열이 꽉 찰 때만 더 큰 int[]와 Object[]
    private fun sparseArrayPutNewKeys(sparseArray: SparseArray<String>, keys: IntArray) {
        for (key in keys) sparseArray.put(key, VALUE)
    }

    // 기대: 할당 없음
    private fun sparseArrayPutSameKeys(sparseArray: SparseArray<String>, keys: IntArray) {
        for (key in keys) sparseArray.put(key, VALUE)
    }

    // 기대: 할당 없음
    private fun sparseArrayGetKeys(sparseArray: SparseArray<String>, keys: IntArray) {
        for (key in keys) checkNotNull(sparseArray.get(key))
    }

    /** 네 컨테이너를 n개로 채워 HeldContainers에 붙잡아 둔다. 측정값은 채우는 동안의 할당이다. */
    fun hold(n: Int): List<ObservationRow> {
        warmUp()
        return holdRows(keys(n))
    }

    private fun holdRows(keys: IntArray): List<ObservationRow> {
        HeldContainers.clear()
        val n = keys.size
        var hashMap: HashMap<Int, String>? = null
        var sparseArray: SparseArray<String>? = null
        var intHashMap: HashMap<Int, Int>? = null
        var sparseIntArray: SparseIntArray? = null
        val rows = listOf(
            ObservationRow("HashMap<Int, String>",
                AllocationCounter.measure { hashMap = fillHashMap(keys) },
                Allocation(1, ExpectedMemory.HASH_MAP) + ExpectedMemory.hashMapPutNew(n),
                ExpectedMemory.hashMapRetained(n)),
            ObservationRow("SparseArray<String>",
                AllocationCounter.measure { sparseArray = fillSparseArray(keys) },
                ExpectedMemory.sparseConstructor + ExpectedMemory.sparsePutNew(n),
                ExpectedMemory.sparseRetained(n)),
            ObservationRow("HashMap<Int, Int>",
                AllocationCounter.measure { intHashMap = fillIntHashMap(keys) },
                Allocation(1, ExpectedMemory.HASH_MAP) + ExpectedMemory.hashMapPutNew(n, boxedValues = true),
                ExpectedMemory.hashMapRetained(n, boxedValues = true)),
            ObservationRow("SparseIntArray",
                AllocationCounter.measure { sparseIntArray = fillSparseIntArray(keys) },
                ExpectedMemory.sparseConstructor + ExpectedMemory.sparsePutNew(n, intValues = true),
                ExpectedMemory.sparseRetained(n, intValues = true)),
        )
        HeldContainers.hashMap = hashMap
        HeldContainers.sparseArray = sparseArray
        HeldContainers.intHashMap = intHashMap
        HeldContainers.sparseIntArray = sparseIntArray
        HeldContainers.keys = keys
        return rows
    }

    private fun fillHashMap(keys: IntArray) = HashMap<Int, String>().apply {
        for (key in keys) this[key] = VALUE
    }

    private fun fillSparseArray(keys: IntArray) = SparseArray<String>().apply {
        for (key in keys) put(key, VALUE)
    }

    // 키와 값을 각각 박싱한다. 값도 캐시 밖이라 Integer가 두 개씩 생긴다.
    private fun fillIntHashMap(keys: IntArray) = HashMap<Int, Int>().apply {
        for (key in keys) this[key] = key
    }

    private fun fillSparseIntArray(keys: IntArray) = SparseIntArray().apply {
        for (key in keys) put(key, key)
    }

    /** 붙잡은 컨테이너에서 앞쪽 90%를 지운다. 측정값은 지우는 동안의 할당이다. */
    fun deleteMost(): List<ObservationRow> {
        // 붙잡은 컨테이너는 이미 채워져 있으므로 hold에서 워밍업이 끝난 상태다.
        return deleteRows(HeldContainers.keys)
    }

    private fun deleteRows(keys: IntArray): List<ObservationRow> {
        val n = keys.size
        val count = deleteCount(n)
        val hashMap = checkNotNull(HeldContainers.hashMap)
        val sparseArray = checkNotNull(HeldContainers.sparseArray)
        val intHashMap = checkNotNull(HeldContainers.intHashMap)
        val sparseIntArray = checkNotNull(HeldContainers.sparseIntArray)
        val rows = listOf(
            ObservationRow("HashMap<Int, String>",
                AllocationCounter.measure { for (i in 0 until count) hashMap.remove(keys[i]) },
                ExpectedMemory.boxedKeys(count),
                ExpectedMemory.hashMapRetained(n - count, tableFor = n)),
            ObservationRow("SparseArray<String>",
                // size()가 DELETED 표시를 정리하는 gc()를 부른다. 정리도 할당 없이 배열 안에서 끝난다.
                AllocationCounter.measure { for (i in 0 until count) sparseArray.remove(keys[i]); sparseArray.size() },
                Allocation.NONE,
                ExpectedMemory.sparseRetained(n)),
            ObservationRow("HashMap<Int, Int>",
                AllocationCounter.measure { for (i in 0 until count) intHashMap.remove(keys[i]) },
                ExpectedMemory.boxedKeys(count),
                ExpectedMemory.hashMapRetained(n - count, boxedValues = true, tableFor = n)),
            ObservationRow("SparseIntArray",
                AllocationCounter.measure { for (i in 0 until count) sparseIntArray.delete(keys[i]) },
                Allocation.NONE,
                ExpectedMemory.sparseRetained(n, intValues = true)),
        )
        check(hashMap.size == n - count && sparseArray.size() == n - count) { "삭제 결과가 예상과 다릅니다." }
        HeldContainers.deleted = true
        return rows
    }
}
