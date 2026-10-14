package com.example.sparsearraylab

import android.util.SparseArray
import java.util.HashMap

/**
 * Kotlin `Int`가 어디서 `Integer`로 박싱되는지 바이트코드로 확인하는 예제.
 *
 * 확인 방법 1 (Android Studio):
 * 1. 이 파일을 연 상태에서 Tools → Kotlin → Show Kotlin Bytecode
 * 2. 바이트코드 창에서 `INVOKESTATIC java/lang/Integer.valueOf` 검색
 * 3. Decompile 버튼을 누르면 Java로 본 결과가 열린다.
 *    디컴파일러에 따라 Integer.valueOf를 생략하고 자동 박싱 형태로 보여 줄 수 있으니
 *    박싱 여부는 2번의 바이트코드로 판단한다.
 *
 * 확인 방법 2 (터미널, README의 "오토박싱 확인" 참고):
 * ./gradlew :app:compileDebugKotlin 후 javap -c 로 이 클래스를 역어셈블한다.
 *
 * 함수마다 주석에 바이트코드에서 실제로 확인한 호출을 적었다.
 */
object AutoBoxingExample {
    fun putToHashMap(map: HashMap<Int, String>) {
        // 바이트코드: Integer.valueOf(I) → Map.put(Object, Object)
        // HashMap.class에는 put(Object, Object) 하나뿐이다 (K, V가 Object로 소거되어 컴파일됨).
        // <Int, String>은 컴파일러 검사용이라 int를 그대로 받는 put이 없어 Integer로 박싱한다.
        map[1042] = "A"
    }

    fun getFromHashMap(map: HashMap<Int, String>): String? {
        // 바이트코드: Integer.valueOf(I) → HashMap.get(Object) → checkcast String
        // 조회할 때도 키를 박싱한다. 반환값이 Object라 컴파일러가 String 캐스트를 넣는다.
        return map[1042]
    }

    fun putToSparseArray(sparseArray: SparseArray<String>) {
        // 바이트코드: SparseArray.put(int, Object)
        // 키 자리가 int라서 Integer.valueOf가 없다.
        sparseArray.put(1042, "A")
    }

    fun getFromSparseArray(sparseArray: SparseArray<String>): String? {
        // 바이트코드: SparseArray.get(int) → checkcast String
        // 키는 int라 박싱이 없지만, 값 E는 제네릭이라 Object로 소거되어 캐스트가 들어간다.
        return sparseArray[1042]
    }

    fun primitiveInt(): Int {
        // 바이트코드: sipush 1042만 있고 박싱 없음
        val number: Int = 1042
        return number + 1
    }

    fun nullableInt(): Int? {
        // 바이트코드: Integer.valueOf(I), 반환 타입 java.lang.Integer
        // null을 담으려면 참조여야 하므로 Int?는 Integer가 된다.
        val number: Int? = 1042
        return number
    }

    fun intArray(): IntArray {
        // 바이트코드: newarray int, 박싱 없음
        return intArrayOf(1042, 2087)
    }

    fun genericList(): List<Int> {
        // 바이트코드: anewarray Integer + 원소마다 Integer.valueOf(I) → CollectionsKt.listOf(Object[])
        // 제네릭 자리라서 원소마다 박싱한다.
        return listOf(1042, 2087)
    }
}
