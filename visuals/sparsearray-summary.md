# SparseArray vs HashMap 정리

> 안드로이드 면접 질문 21. SparseArray를 사용하면 어떤 이점이 있나요?

인터랙티브 버전:
- [SparseArray vs HashMap 메모리](https://claude.ai/artifact/XA1FADjXrwygG9g4woaPg6): 4바이트 칸 단위 힙 그림, 항목 수별 메모리 그래프
- [SparseArray 동작 원리](https://claude.ai/artifact/5mCmVPytxRkPGrwT6BAFRB): put/get/remove를 두 구조에 동시에 실행해 보는 놀이터

---

## 1. 한 줄 요약

| | 찾는 방법 | 항목마다 생기는 객체 |
|---|---|---|
| `HashMap<Int, V>` | key → `Integer` 박싱 → `hashCode()` → `& (n-1)` → 버킷 → `Node` 체인 | `Node` 1개 + `Integer` 1개 |
| `SparseArray<V>` | key(int 그대로) → 정렬된 `mKeys[]`에서 이진 탐색 → 같은 인덱스의 `mValues[i]` | 없음 |

HashMap은 **해시로 자리를 계산**하고, SparseArray는 **정렬된 키 배열에서 이진 탐색**으로 찾는다.
메모리 차이, 속도 차이, 삭제 방식 모두 이 설계 차이에서 나온다.

---

## 2. 이름의 뜻: Sparse = 키가 드문드문하다

키가 `3`, `17`, `4000` 세 개뿐이라고 하자.

**키를 그대로 인덱스로 쓰는 배열** `arrayOfNulls<String>(4001)`

```
index:  0  1  2  3  4 ... 17 ... 3999 4000
value:  .  .  .  A  .  ...  B ...   .    C
```

- 칸 4,001개 중 3개만 사용, 배열 크기 약 16,016 B

**SparseArray** (있는 키만 정렬해서 저장)

```
mKeys   (int[])    : [ 3 ][ 17 ][ 4000 ]
mValues (Object[]) : ["A"]["B" ]["C"  ]
```

- 3쌍만 저장. 배열 두 개 합쳐 96 B (Android 14 이상: 첫 put에 9칸씩) / 112 B (Android 13 이하: 처음부터 11칸씩)
- 대신 키 값만으로는 몇 번째 칸에 있는지 알 수 없어서 **찾아야 한다** → 이진 탐색

---

## 3. 오토박싱: int는 값, Integer는 객체

Kotlin의 `Int`는 평소 원시 타입 `int`로 컴파일되지만, 제네릭 `HashMap<Int, V>`의 키 자리에는 객체만 들어갈 수 있어서 `Integer.valueOf(key)`로 감싸진다.

```
SparseArray의 키 (int[])
[헤더][1042][2087][5120]          ← 칸에 숫자 자체가 들어 있음 (칸당 4B)

HashMap의 키 (Node.key → Integer)
[→①][→②][→③]                     ← 칸에는 참조만
 ① Integer: [헤더 8B][1042][패딩]  ← 16B짜리 객체가 키마다 하나씩
 ② Integer: [헤더 8B][2087][패딩]
 ③ Integer: [헤더 8B][5120][패딩]
```

### 키를 넘기는 연산은 전부 박싱한다

HashMap에서 키를 받는 메서드는 모두 `Object`를 받는다(이유는 아래 "타입 소거"). 그래서 조회만이 아니라 **키를 넘기는 모든 호출**에서 박싱이 일어난다.

| 코드 | `HashMap<Int, V>` | `SparseArray<V>` / `SparseIntArray` |
|---|---|---|
| 새 키 `map[1042] = "A"` | 키 박싱 + `Node` 생성 | 박싱 없음, 배열 칸에 `1042` 기록 |
| 이미 있는 키 `put`, `map[1042]`, `1042 in map`, `map.remove(1042)`, `getOrDefault`, `putIfAbsent`, `compute…`, `merge` 등 | **호출마다** 키 박싱. 저장되지 않는 임시 객체라 금방 버려져 GC 부담 | `get`, `indexOfKey`, `delete` 등 모두 `int`를 받아 할당 없음 |
| `HashMap<Int, Int>` 카운터 `m[k] = (m[k] ?: 0) + 1` | 키 박싱 2 + 값 언박싱 1 + 값 박싱 1 | `SparseIntArray`: 없음 |
| 순회 `for ((k, v) in map)` | Iterator 할당 + 키마다 언박싱 | `keyAt(i)` / `valueAt(i)`: 없음 |
| 키가 `-128~127` | `Integer` 캐시 재사용 → 새 객체 없음 (`valueOf` 호출은 그대로) | 할당 없음 |

- `remove`는 임시 Integer에 더해, 맵 안에 있던 Integer와 Node도 GC 대상으로 만든다.
- `getOrPut(1042) { … }`은 inline 함수라 키를 한 번만 박싱해 `get`과 `put`에 재사용한다.

아래 바이트코드는 같은 코드를 임시 파일로 컴파일해 `javap -c`로 확인한 결과다 (발췌, 화살표 주석은 설명용).

**카운터: 한 줄에 박싱 3번**

값까지 숫자라면 `HashMap<Int, Int>`는 값도 박싱한다.

```kotlin
val counts = HashMap<Int, Int>()
counts[1042] = (counts[1042] ?: 0) + 1
```

```
invokestatic  java/lang/Integer.valueOf:(I)Ljava/lang/Integer;   ← put에 쓸 키 박싱
invokestatic  java/lang/Integer.valueOf:(I)Ljava/lang/Integer;   ← get에 쓸 키 박싱 (같은 키를 또)
invokevirtual java/util/HashMap.get:(Ljava/lang/Object;)Ljava/lang/Object;
invokevirtual java/lang/Integer.intValue:()I                     ← 꺼낸 값 언박싱
invokestatic  java/lang/Integer.valueOf:(I)Ljava/lang/Integer;   ← 새 값 박싱
invokeinterface java/util/Map.put:(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;
```

`SparseIntArray`는 키·값 모두 `int[]`에 담아 둘 다 피한다.

```kotlin
counts.put(1042, counts.get(1042) + 1)
```

```
invokevirtual android/util/SparseIntArray.get:(I)I
invokevirtual android/util/SparseIntArray.put:(II)V
```

**순회: 박싱은 없지만 언박싱과 할당이 있다**

```kotlin
for ((k, _) in map) sum += k
```

```
invokeinterface java/util/Map.entrySet:()Ljava/util/Set;
invokeinterface java/util/Set.iterator:()Ljava/util/Iterator;    ← Iterator 객체 할당
invokeinterface java/util/Iterator.next:()Ljava/lang/Object;
invokeinterface java/util/Map$Entry.getKey:()Ljava/lang/Object;
invokevirtual java/lang/Number.intValue:()I                      ← 키마다 언박싱
```

키는 이미 Node 안에 Integer로 있으니 새로 박싱하지는 않는다. SparseArray는 인덱스로 돌면 `SparseArray.keyAt:(I)I`라서 할당도 언박싱도 없다.

### 왜 제네릭 자리에는 객체만 들어갈까: 타입 소거

JVM의 제네릭은 **타입 소거(type erasure)** 방식이다. 컴파일러는 제네릭 타입으로 **검사만** 하고, 컴파일 결과에서는 그 타입 정보를 지운다. HashMap의 `K`, `V`는 `Object`로 바뀌고, `Object` 자리에는 객체를 가리키는 참조만 들어갈 수 있다.

컴파일러가 하는 일은 세 단계다.

| 단계 | 하는 일 | `HashMap<Int, String>`에서 |
|---|---|---|
| 1. 검사 | 타입 인자를 대입해 맞는지 확인 | `map["hello"] = "A"`는 컴파일 에러 |
| 2. 소거 | `K`, `V`를 `Object`로 바꾸고, `<Int, String>`은 지움 | `put(Object, Object)`, 파라미터 타입은 그냥 `HashMap` |
| 3. 캐스트 삽입 | 꺼낸 `Object`를 원래 타입으로 형변환 | `(String) map.get(…)` |

> 항상 `Object`가 되는 것은 아니다. 상한(bound)이 없으면 `Object`, `class Sorted<T : Comparable<T>>`처럼 상한이 있으면 그 타입(`Comparable`)으로 소거된다. HashMap의 `K`, `V`는 상한이 없어서 `Object`다.

**1) 컴파일하면 `K`가 사라진다**

HashMap 클래스는 `HashMap<Int, ...>`용, `HashMap<String, ...>`용이 따로 있지 않다. 컴파일된 클래스는 하나뿐이고, 그 안의 `K`는 `Object`다. 소거는 선언하는 쪽과 사용하는 쪽 양쪽에서 일어난다.

| | 컴파일 전 | 컴파일 후 |
|---|---|---|
| 선언하는 쪽 (HashMap.java) | `put(K key, V value)` | `put(Object, Object)` |
| 사용하는 쪽 (내 코드) | `map: HashMap<Int, String>` | `map: HashMap` |

HashMap.java는 앱보다 훨씬 먼저, 누가 어떤 타입으로 쓸지 모르는 상태에서 한 번 컴파일되어 기기(Android 프레임워크)에 들어 있다. 내 코드에 `HashMap<Int, String>`이라고 써도 int용 HashMap이 새로 생기지 않는다. `<Int, String>`은 컴파일러가 검사할 때 쓰는 꼬리표이고, 실행되는 건 `Object`만 다루는 HashMap 하나다.

```kotlin
HashMap<Int, String>().javaClass == HashMap<String, User>().javaClass   // true: 둘 다 java.util.HashMap
```

```java
// 컴파일 후 HashMap 내부를 소거된 타입으로 표현 (설명용)
static class Node {
    final int hash;
    final Object key;     // K → Object
    Object value;         // V → Object
    Node next;
}
public Object put(Object key, Object value) { ... }
```

> 설명용 코드: 소거 결과를 Java로 표현한 것이다. 원본 제네릭 선언은 [HashMap.java L280–284](https://android.googlesource.com/platform/libcore/+/de876a01b29230b877c9f408348c38a90ee724a5/ojluni/src/main/java/java/util/HashMap.java#280) (`static class Node<K,V> implements Map.Entry<K,V>`).

그래서 Kotlin의 `map[1042] = "A"`는 이렇게 바뀐다.

```java
map.put(Integer.valueOf(1042), "A");   // int를 넘길 수 없으니 Integer로 감싸서 넘김
```

> 설명용 코드: Kotlin 컴파일 결과를 Java로 표현한 것이다. 실제 바이트코드는 아래와 같다.

```
// app/.../AutoBoxingExample.kt의 putToHashMap()을 javap -c 로 역어셈블한 결과 (발췌)
sipush        1042
invokestatic  java/lang/Integer.valueOf:(I)Ljava/lang/Integer;
invokeinterface java/util/Map.put:(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;
```

`Map.put`의 시그니처가 `(Object, Object)`인 것이 타입 소거의 증거다. 컴파일러는 키가 `Int`인 걸 알지만, HashMap에는 `put(int, …)`이 없고 `put(Object, Object)`뿐이라 박싱해서 넘길 수밖에 없다. 같은 파일의 `putToSparseArray()`는 `SparseArray.put:(ILjava/lang/Object;)V`(`I` = int)로, 박싱이 없다.

**디컴파일 결과에서 보이는 소거 흔적**

같은 파일을 Show Kotlin Bytecode → **Decompile**로 보면 이렇게 나온다 (null 검사 줄 생략).

```java
public final void putToHashMap(@NotNull HashMap map) {    // <Int, String>이 없다
   ((Map)map).put(1042, "A");                              // Integer.valueOf는 생략되어 보인다
}

public final String getFromHashMap(@NotNull HashMap map) {
   return (String)map.get(1042);                           // 컴파일러가 넣은 캐스트
}

public final String getFromSparseArray(@NotNull SparseArray sparseArray) {
   return (String)sparseArray.get(1042);                   // SparseArray도 값은 캐스트
}
```

- `(String)`은 직접 쓴 코드가 아니라 3단계에서 삽입된 캐스트다. 바이트코드로는 `checkcast java/lang/String`이다. `get`의 반환 타입이 소거되어 `Object`이기 때문이다.
- SparseArray도 **값은 제네릭 `E`**라 똑같이 소거되어 캐스트가 붙는다. 키만 `int`로 따로 선언했을 뿐이다.

> 정보가 완전히 사라지는 것은 아니다. 클래스 파일에는 `Signature` 메타데이터로 선언부의 제네릭 정보가 남는다. 그래서 `javap`로 보면 `putToHashMap(java.util.HashMap<java.lang.Integer, java.lang.String>)`처럼 나오고(Decompile 결과는 raw `HashMap`으로 보여 준다), 리플렉션이나 Gson의 `TypeToken`이 이 정보를 읽는다.
> 지워지는 것은 **실행되는 코드**(`put(Object, Object)` 호출, 캐스트)와 **객체 인스턴스의 타입 인자**다. `HashMap<Int, String>()` 객체 자체는 자기가 `<Int, String>`인지 모른다. 그래서 `x is List<String>` 같은 검사는 컴파일 에러다.

> 직접 확인: 이 레포의 `app/src/main/java/com/example/sparsearraylab/AutoBoxingExample.kt`를 열고 Android Studio의 **Tools → Kotlin → Show Kotlin Bytecode**에서 `Integer.valueOf`를 검색한다.
> Decompile 버튼은 디컴파일러에 따라 `Integer.valueOf`를 생략해 보여 줄 수 있으니 바이트코드로 판단한다. 터미널 확인법과 실행 테스트(`오토박싱학습테스트`)는 README의 "오토박싱 확인"에 있다.

**2) `Object` 칸에 int를 그냥 넣을 수 없는 이유**

둘 다 4바이트지만 의미가 다르다.

| | `int 1042` | `Integer` 참조 |
|---|---|---|
| 칸에 든 것 | 숫자 1042 자체 | 힙에 있는 Integer 객체의 주소 |
| 헤더 (클래스 정보) | 없음 | 객체에 있음 |
| 메서드 호출 | 불가 | `hashCode()`, `equals()` 호출 가능 |

- **메서드 호출**: HashMap은 키 타입을 모른 채 `key.hashCode()`, `key.equals(k)`를 호출한다. 객체 헤더의 클래스 정보를 따라가 그 타입의 메서드를 찾는 가상 호출이다. 숫자 1042에는 따라갈 헤더가 없다.
- **GC**: GC는 `Object key` 필드를 "다른 객체의 주소"로 보고 따라가서 살아 있는 객체를 표시한다. 그 칸에 1042를 그대로 넣으면 GC가 1042번지를 객체로 착각해 메모리가 망가진다. 그래서 JVM은 참조 칸과 원시값 칸을 엄격히 구분한다.

따라서 int를 `Object` 자리에 넣으려면 헤더를 가진 진짜 객체(`Integer`)로 감싸야 한다. 이것이 박싱이다.

**3) 왜 HashMap은 `Object` 버전 하나뿐인가**

타입별 HashMap이 없는 것과 타입 소거는 같은 이야기다. 소거 방식이라 HashMap이 하나뿐이고, 그래서 int 키는 박싱된다. Java가 이렇게 만든 이유는 세 가지다.

**① HashMap이 제네릭보다 먼저였다.** HashMap은 Java 1.2(1998년)부터 `Object`를 받는 클래스였다. 담을 타입은 사용자가 만든 클래스까지 무한하니 타입별로 미리 만들어 둘 수 없었고, 모든 클래스의 부모인 `Object`로 받게 했다.

```java
// Java 1.4까지: 제네릭도 오토박싱도 없음
HashMap map = new HashMap();
map.put(new Integer(1042), "A");                 // 박싱을 직접
String s = (String) map.get(new Integer(1042));  // 캐스트도 직접
map.put("oops", new Date());                     // 잘못 넣어도 컴파일 통과 → 실행 중 ClassCastException
```

Java 5(2004년)의 제네릭과 오토박싱은 사람이 하던 타입 확인·캐스트·박싱을 컴파일러가 대신 하게 한 것이다. 그래서 컴파일 결과가 위 코드와 같다. 디컴파일에서 본 `(String)map.get(1042)`가 이 시절 사람이 손으로 쓰던 코드다.

**② 객체 타입끼리는 HashMap 코드가 똑같다.** HashMap이 키로 하는 일은 저장, `hashCode()`, `equals()`뿐이다. 어떤 객체든 칸에는 같은 크기(4B)의 참조가 들어가고, 메서드는 객체 헤더의 클래스 정보를 따라가 찾는 같은 가상 호출이다. String용과 User용을 따로 컴파일해도 결과가 같으니 하나면 충분하다. C#도 참조 타입끼리는 코드 하나를 공유한다.

**③ int용을 따로 만드는 건 호환성 때문에 포기했다.** int는 참조가 아니고 헤더도 없어서(`long`, `double`은 크기도 8B) 박싱 없이 다루려면 실제로 다른 코드가 필요하다. 하지만 `HashMap<Integer, String>`을 별도 클래스로 만들면 `void save(HashMap map)` 같은 기존 라이브러리에 새 맵을 넘길 수 없게 되고, 쓸 때마다 전용 코드를 만들려면 JVM 자체를 바꿔야 했다. 그래서 Java는 클래스는 `Object` 버전 하나로 두고 제네릭은 컴파일 시점 검사로만 쓰며, int는 박싱해서 맞추기로 했다. Kotlin도 JVM 위에서 돌기 때문에 같은 규칙을 따른다.

```
호환성을 지켜야 했다 → 타입 소거를 택했다 → HashMap은 Object 버전 하나뿐
→ int 키는 박싱된다 → int 키 전용 SparseArray를 따로 만들었다
```

| 언어 | 방식 | `Map<int, ...>` |
|---|---|---|
| Java / Kotlin (JVM) | 소거: 클래스 하나, `Object`로 통일 | 키를 박싱해야 함 |
| C++ 템플릿 | 쓰는 타입마다 코드를 따로 생성 | int용 코드가 따로 생겨서 박싱 없음 |
| C# | 런타임에 타입 정보 유지. 참조 타입은 코드 공유, 값 타입이면 전용 코드 생성 | `Dictionary<int, V>`가 박싱 없음 |

C++과 C#은 컴파일러나 런타임이 타입별 버전을 **자동으로** 만든다. Java에서 int 전용 맵이 필요하면 사람이 **직접** 만들어야 한다. JVM에서도 원시 타입을 제네릭에 쓰게 하려는 작업(Project Valhalla)이 진행 중이다.

**4) Kotlin에서 `Int`가 객체가 되는 경우**

| Kotlin 코드 | JVM 타입 |
|---|---|
| `val a: Int = 1042` | `int` (원시) |
| `IntArray(10)` | `int[]` (원시) |
| `val b: Int? = 1042` | `Integer` (null을 담으려면 참조여야 함) |
| `List<Int>`, `HashMap<Int, V>`, `Array<Int>` | `Integer` (제네릭 자리) |

**5) 그래서 SparseArray가 나왔다**

SparseArray는 키를 제네릭으로 받지 않고 **`int[] mKeys`로 직접 선언**했다. 제네릭에 맡기면 박싱이 생기니 int 키 전용 컨테이너를 따로 만든 셈이다.
값은 여전히 `V`(제네릭)라 객체이고, 값까지 원시 타입이면 값도 `int[]`로 선언한 `SparseIntArray` 같은 전용 클래스를 쓴다.
androidx의 `IntObjectMap`도 같은 생각으로 만든 int 키 전용 해시 테이블이다.
객체 타입은 무한해서 타입별로 만들 수 없지만, 원시 타입은 8개뿐이라 사람이 직접 전용 버전을 만들 수 있다. Java 8의 `IntStream`, `IntFunction`도 같은 이유로 생겼다.

---

## 4. 메모리: 숫자로 보기

### 계산 기준 (ART)

- 객체 헤더 8B (클래스 참조 4 + lock word 4)
- 힙 참조 4B (64비트 기기도 동일)
- 배열 헤더 12B (헤더 8 + length 4)
- 객체 크기는 8B 단위로 올림
- SparseArray 계열 배열은 `newUnpaddedArray`로 만들어 올림으로 남는 칸까지 쓴다 (8칸 요청 → 48B → 9칸)
- 12KB 이상인 원시 타입 배열(`int[]`)은 Large Object Space에 **페이지 단위**로 잡힌다. `Object[]`는 해당하지 않는다

| 객체 | 크기 | 구성 |
|---|---|---|
| `HashMap` 자체 | 40B | table, entrySet, keySet, values 참조 + size, modCount, threshold, loadFactor |
| `HashMap.Node` | 24B | 헤더 8 + hash + key 참조 + value 참조 + next 참조 |
| `Integer` | 16B | 헤더 8 + int 4 + 패딩 4 |
| `Node[]` 테이블 | 12 + 4 × 용량 | 첫 put에 16칸, 항목 수 > 용량 × 0.75면 두 배 |
| `SparseArray` 자체 | 24B | mKeys, mValues 참조 + mSize + mGarbage |
| `int[]` / `Object[]` | 12 + 4 × 용량 | Android 14 이상은 빈 배열로 시작해 첫 put에 9칸, 꽉 차면 약 두 배. 13 이하는 11칸에서 시작 |

### 항목 1개당 비용

| | HashMap | SparseArray |
|---|---|---|
| 실제 키·값 데이터 | 8B (Integer 안의 int 4 + Node.value 참조 4) | 8B (int 4 + 값 참조 4) |
| 그 외 | Node 헤더·hash·key·next 16B + Integer 헤더·패딩 12B + 테이블 슬롯 | 없음 |
| 합계 (빈 용량 포함) | **약 44~51B** | **약 8~16B** |

> 실제 데이터는 양쪽 모두 항목당 8B로 같다. 차이는 전부 "구조를 유지하는 비용"이다.

### 항목 수별 총 메모리

키는 리소스 ID처럼 큰 정수(Integer 캐시 밖), 값 객체(String)는 양쪽이 똑같이 가리키므로 제외. 해시 충돌 없음 가정.
SparseArray는 **Android 14 이상, 4KB 페이지** 기준. 키 배열과 값 배열은 각자 자기 길이를 보고 늘어나서 큰 N에서는 길이가 달라진다.

| 항목 수 | HashMap 테이블 | SparseArray 배열 (키 / 값) | `HashMap<Int, String>` | `SparseArray<String>` | 배수 |
|---:|---:|---:|---:|---:|---:|
| 0 | 없음 | 0칸 (공유 빈 배열) | 40 B | 24 B | 1.7× |
| 1 | 16칸 | 9칸 | 160 B | 120 B | 1.3× |
| 10 | 16칸 | 19칸 | 520 B | 200 B | 2.6× |
| 100 | 256칸 | 159칸 | 5,080 B | 1,320 B | 3.8× |
| 1,000 | 2,048칸 | 1,279칸 | 48,248 B (47.1 KB) | 10,280 B (10.0 KB) | **4.7×** |
| 10,000 | 16,384칸 | 12,285 / 10,239칸 | 465,592 B (454.7 KB) | 90,144 B (88.0 KB) | 5.2× |
| 100,000 | 262,144칸 | 196,605 / 163,839칸 | 5,048,632 B (4.8 MB) | 1,441,824 B (1.4 MB) | 3.5× |

- **16KB 페이지 기기** (Android 15부터 지원, 이 레포의 API 37 에뮬레이터): 10,000개 106,528 B (4.4×), 100,000개 1,179,680 B (4.3×). 큰 `int[]`가 16KB 단위로 잡혀서다.
- **Android 13 이하** (11칸에서 시작): 0 · 1 · 10개 모두 136 B, 100개 1,576 B, 1,000개 12,328 B, 10,000개 114,720 B.
- 값도 Int라면 (`HashMap<Int, Int>` vs `SparseIntArray`) 값 박싱 16B × N이 더해져 1,000개 기준 64,248 B vs 10,280 B (6.2×).
- 같은 계산식으로 구한 **채우는 동안의 할당량**은 앱 카드 ②의 측정값과 바이트 단위까지 일치했다(API 37 에뮬레이터, N = 10~10,000). 표의 값은 거기서 확장하며 버린 작은 배열을 뺀, 남아 있는 크기다. N = 1,000이면 HashMap은 56,488 B를 할당하고 48,248 B가 남는다.

### 눈여겨볼 점

- **비어 있을 때는 버전마다 다르다.** Android 14부터 `SparseArray()`는 `this(0)`으로 모든 인스턴스가 공유하는 빈 배열을 가리켜 24B뿐이다. 13 이하는 `this(10)`이라 생성 즉시 11칸 배열 2개를 만들어(136B) HashMap(40B, 첫 `put`까지 테이블 없음)보다 컸다.
- **항목이 적으면 고정비가 지배한다.** 1개일 때는 160B vs 120B로 차이가 작다.
- **배수가 톱니 모양으로 바뀐다.** 두 구조의 배열이 두 배로 커지는 시점이 서로 달라서다.
- **`-128~127` 키는 박싱 비용이 없다.** `Integer` 캐시를 공유하기 때문이다.
- **큰 `int[]`는 페이지 단위다.** 12KB를 넘는 순간 페이지 크기로 올림되고 `newUnpaddedArray`가 그 끝까지 채워, 같은 길이를 요청한 `Object[]`보다 길어진다.
- **실측은 앱으로.** 카드 ②에서 컨테이너를 붙잡아 두고 Profiler의 Analyze Memory Usage로 retained size를 본다. 할당량은 앱이 직접 세어 이 계산과 비교한다.

---

## 5. 동작 원리

> 이 섹션의 Java 코드는 AOSP 원본에서 **주석(Javadoc)만 빼고 그대로** 옮겼다.
> 코드 아래 출처 링크는 특정 커밋에 고정되어 있어 줄 번호가 바뀌지 않는다.
> 라이선스: Apache License 2.0 (© The Android Open Source Project), HashMap은 OpenJDK 기반 GPLv2 + Classpath Exception.

### HashMap: "엔트리를 Node 객체의 배열에 저장한다"는 말 뜯어보기

#### 용어 정리

| 용어 | 실체 | 하는 일 |
|---|---|---|
| 엔트리 (entry) | 키-값 한 쌍 | `1042 → "A"` 같은 데이터 하나 |
| Node | 엔트리를 담는 **객체** | 키, 값, 해시, 다음 Node 참조를 들고 있음 |
| table | `Node[]` **배열 객체** | HashMap의 뼈대 |
| 버킷 (bucket) | table의 **칸 하나** | 해시로 계산된 자리. 첫 Node를 가리킴 |

"Node(버킷) 객체의 배열"이라는 표현은 Node와 버킷을 같은 것처럼 썼지만 둘은 다르다.
**버킷은 배열의 칸이고, Node는 그 칸이 가리키는 객체다.** 배열 안에 Node가 직접 들어 있는 게 아니라 Node를 가리키는 참조(주소)가 들어 있다.

#### Node 객체 안에 무엇이 있나

```java
    static class Node<K,V> implements Map.Entry<K,V> {
        final int hash;
        final K key;
        V value;
        Node<K,V> next;
        // ... (생성자, getKey/getValue 등 생략)
```

> 출처: [HashMap.java L280–284](https://android.googlesource.com/platform/libcore/+/de876a01b29230b877c9f408348c38a90ee724a5/ojluni/src/main/java/java/util/HashMap.java#280)

```java
    transient Node<K,V>[] table;
```

> 출처: [HashMap.java L389–389](https://android.googlesource.com/platform/libcore/+/de876a01b29230b877c9f408348c38a90ee724a5/ojluni/src/main/java/java/util/HashMap.java#389)

| 필드 | 크기 | 왜 있나 |
|---|---|---|
| (객체 헤더) | 8B | 모든 객체에 붙음. 클래스 정보 + lock word |
| `hash` | 4B | 해시를 매번 다시 계산하지 않으려고 저장. 비교할 때 hash부터 보고 다르면 `equals()` 없이 넘어감. 테이블 확장 시 재배치에도 씀 |
| `key` | 4B | 키 객체 참조. `HashMap<Int, …>`이면 박싱된 `Integer` |
| `value` | 4B | 값 객체 참조 |
| `next` | 4B | 해시 충돌 시 같은 버킷의 다음 Node로 이어지는 연결 리스트 |
| **합계** | **24B** | |

`implements Map.Entry`이기 때문에 "엔트리"라고 부른다. `for ((k, v) in map)`으로 순회할 때 받는 것이 이 Node 객체다.

#### 버킷 번호 계산

```java
    static final int hash(Object key) {
        int h;
        return (key == null) ? 0 : (h = key.hashCode()) ^ (h >>> 16);
    }
```

> 출처: [HashMap.java L335–338](https://android.googlesource.com/platform/libcore/+/de876a01b29230b877c9f408348c38a90ee724a5/ojluni/src/main/java/java/util/HashMap.java#335)

버킷 번호는 `(n - 1) & hash`다 (n = 테이블 용량, 2의 거듭제곱). `Integer.hashCode()`는 값 자체라서, 작은 키는 `h >>> 16`이 0이 되어 해시 = 키가 된다.

#### 실제로 넣어 보면

```kotlin
val map = HashMap<Int, String>()
map[1042] = "A"   // 1042 & 15 = 2
map[2087] = "B"   // 2087 & 15 = 7
map[1058] = "C"   // 1058 & 15 = 2 → 1042와 충돌
```

```
HashMap 객체 (40B)
  table ─┐
         ▼
  Node[16] 배열 객체 (12 + 16×4 = 76 → 80B)
  ┌─────┬─────┬─────┬─────┬─────┬─────┬─────┬─────┬─────┬ ─ ─ ┐
  │ [0] │ [1] │ [2] │ [3] │ [4] │ [5] │ [6] │ [7] │ [8] │ ... │
  │null │null │  ●  │null │null │null │null │  ●  │null │     │
  └─────┴─────┴──┼──┴─────┴─────┴─────┴─────┴──┼──┴─────┴ ─ ─ ┘
                 ▼                             ▼
        ┌─ Node (24B) ──┐             ┌─ Node (24B) ──┐
        │ hash  = 1042  │             │ hash  = 2087  │
        │ key   ● ──────┼─▶ Integer(1042)  │ key ● ─▶ Integer(2087)
        │ value ● ──────┼─▶ "A"       │ value ● ─▶ "B"
        │ next  ●       │             │ next  = null  │
        └───────┼───────┘             └───────────────┘
                ▼
        ┌─ Node (24B) ──┐
        │ hash  = 1058  │
        │ key   ● ──────┼─▶ Integer(1058)
        │ value ● ──────┼─▶ "C"
        │ next  = null  │
        └───────────────┘
```

엔트리 3개에 **객체 8개**가 생긴다 (값 String 제외).

| 객체 | 개수 | 크기 |
|---|---|---|
| HashMap | 1 | 40B |
| `Node[]` 테이블 | 1 | 80B (13칸이 null이어도 자리를 차지) |
| Node | 3 | 24B × 3 |
| Integer (박싱된 키) | 3 | 16B × 3 |

#### 왜 엔트리마다 객체를 따로 만드나

1. **충돌 처리**: 한 버킷에 엔트리 여러 개가 들어갈 수 있어야 한다. 칸 하나에는 참조 하나만 들어가므로 엔트리를 객체로 만들고 `next`로 엮는다(체이닝).
   한 버킷에 8개(`TREEIFY_THRESHOLD`, [L259](https://android.googlesource.com/platform/libcore/+/de876a01b29230b877c9f408348c38a90ee724a5/ojluni/src/main/java/java/util/HashMap.java#259))가 쌓이고 테이블이 64칸(`MIN_TREEIFY_CAPACITY`, [L274](https://android.googlesource.com/platform/libcore/+/de876a01b29230b877c9f408348c38a90ee724a5/ojluni/src/main/java/java/util/HashMap.java#274)) 이상이면 더 큰 `TreeNode`로 바뀐다.
2. **JVM에는 구조체가 없다**: C라면 `{hash, key, value, next}` 구조체를 배열 칸에 바로 펼쳐 넣을 수 있다. JVM 배열은 원시값이나 참조만 담을 수 있어서 여러 필드를 묶으려면 별도 객체를 만들어 그 참조를 넣어야 한다.

#### 저장 경로: `map[1042] = "A"` 한 줄에서 생기는 일

키 박싱부터 Node 생성까지 순서대로 따라가면 이렇다.

| 단계 | 하는 일 | 위치 |
|---|---|---|
| 1 | `Integer.valueOf(1042)`: 캐시 범위(-128~127) 밖이라 `new Integer(1042)` → **Integer 16B** | Integer.java [L1194](https://android.googlesource.com/platform/libcore/+/de876a01b29230b877c9f408348c38a90ee724a5/ojluni/src/main/java/java/lang/Integer.java#1194) |
| 2 | `put(key, value)` → `putVal(hash(key), …)` | HashMap.java [L616](https://android.googlesource.com/platform/libcore/+/de876a01b29230b877c9f408348c38a90ee724a5/ojluni/src/main/java/java/util/HashMap.java#616) |
| 3 | `hash()`에서 `key.hashCode()` 호출. `Integer.hashCode()`는 값 그대로 1042를 반환 | HashMap.java [L335](https://android.googlesource.com/platform/libcore/+/de876a01b29230b877c9f408348c38a90ee724a5/ojluni/src/main/java/java/util/HashMap.java#335), Integer.java [L1325](https://android.googlesource.com/platform/libcore/+/de876a01b29230b877c9f408348c38a90ee724a5/ojluni/src/main/java/java/lang/Integer.java#1325), [L1338](https://android.googlesource.com/platform/libcore/+/de876a01b29230b877c9f408348c38a90ee724a5/ojluni/src/main/java/java/lang/Integer.java#1338) |
| 4 | 테이블이 없으면 `resize()`로 `Node[16]` 생성 (첫 `put`일 때만) | HashMap.java [L634](https://android.googlesource.com/platform/libcore/+/de876a01b29230b877c9f408348c38a90ee724a5/ojluni/src/main/java/java/util/HashMap.java#634) |
| 5 | `(n - 1) & hash`로 버킷 인덱스 계산 | HashMap.java [L635](https://android.googlesource.com/platform/libcore/+/de876a01b29230b877c9f408348c38a90ee724a5/ojluni/src/main/java/java/util/HashMap.java#635) |
| 6a | 버킷이 비어 있으면 `newNode(…)`를 그 칸에 저장 | HashMap.java [L636](https://android.googlesource.com/platform/libcore/+/de876a01b29230b877c9f408348c38a90ee724a5/ojluni/src/main/java/java/util/HashMap.java#636) |
| 6b | 충돌이면 같은 키가 있는지 체인을 따라가며 확인하고, 없으면 끝에 `newNode(…)`를 연결 | HashMap.java [L647](https://android.googlesource.com/platform/libcore/+/de876a01b29230b877c9f408348c38a90ee724a5/ojluni/src/main/java/java/util/HashMap.java#647) |
| 6c | **같은 키가 이미 있으면** Node를 만들지 않고 `e.value = value`로 값만 교체 | HashMap.java [L658](https://android.googlesource.com/platform/libcore/+/de876a01b29230b877c9f408348c38a90ee724a5/ojluni/src/main/java/java/util/HashMap.java#658) |
| 7 | `newNode`가 `new Node<>(hash, key, value, next)` 실행 → **Node 24B**. `key` 필드에는 1단계의 Integer 참조가 그대로 들어감 | HashMap.java [L1906](https://android.googlesource.com/platform/libcore/+/de876a01b29230b877c9f408348c38a90ee724a5/ojluni/src/main/java/java/util/HashMap.java#1906) |
| 8 | `++size > threshold`면 `resize()`로 테이블 두 배 확장 | HashMap.java [L667](https://android.googlesource.com/platform/libcore/+/de876a01b29230b877c9f408348c38a90ee724a5/ojluni/src/main/java/java/util/HashMap.java#667) |

```java
    public static Integer valueOf(int i) {
        if (i >= IntegerCache.low && i <= IntegerCache.high)
            return IntegerCache.cache[i + (-IntegerCache.low)];
        return new Integer(i);
    }
```

> 출처: [Integer.java L1194–1198](https://android.googlesource.com/platform/libcore/+/de876a01b29230b877c9f408348c38a90ee724a5/ojluni/src/main/java/java/lang/Integer.java#1194)

```java
    public int hashCode() {
        return Integer.hashCode(value);
    }
```

> 출처: [Integer.java L1325–1327](https://android.googlesource.com/platform/libcore/+/de876a01b29230b877c9f408348c38a90ee724a5/ojluni/src/main/java/java/lang/Integer.java#1325)

```java
    public static int hashCode(int value) {
        return value;
    }
```

> 출처: [Integer.java L1338–1340](https://android.googlesource.com/platform/libcore/+/de876a01b29230b877c9f408348c38a90ee724a5/ojluni/src/main/java/java/lang/Integer.java#1338)

```java
    public V put(K key, V value) {
        return putVal(hash(key), key, value, false, true);
    }
```

> 출처: [HashMap.java L616–618](https://android.googlesource.com/platform/libcore/+/de876a01b29230b877c9f408348c38a90ee724a5/ojluni/src/main/java/java/util/HashMap.java#616)

```java
    final V putVal(int hash, K key, V value, boolean onlyIfAbsent,
                   boolean evict) {
        Node<K,V>[] tab; Node<K,V> p; int n, i;
        if ((tab = table) == null || (n = tab.length) == 0)
            n = (tab = resize()).length;
        if ((p = tab[i = (n - 1) & hash]) == null)
            tab[i] = newNode(hash, key, value, null);
        else {
            Node<K,V> e; K k;
            if (p.hash == hash &&
                ((k = p.key) == key || (key != null && key.equals(k))))
                e = p;
            else if (p instanceof TreeNode)
                e = ((TreeNode<K,V>)p).putTreeVal(this, tab, hash, key, value);
            else {
                for (int binCount = 0; ; ++binCount) {
                    if ((e = p.next) == null) {
                        p.next = newNode(hash, key, value, null);
                        if (binCount >= TREEIFY_THRESHOLD - 1) // -1 for 1st
                            treeifyBin(tab, hash);
                        break;
                    }
                    if (e.hash == hash &&
                        ((k = e.key) == key || (key != null && key.equals(k))))
                        break;
                    p = e;
                }
            }
            if (e != null) { // existing mapping for key
                V oldValue = e.value;
                if (!onlyIfAbsent || oldValue == null)
                    e.value = value;
                afterNodeAccess(e);
                return oldValue;
            }
        }
        ++modCount;
        if (++size > threshold)
            resize();
        afterNodeInsertion(evict);
        return null;
    }
```

> 출처: [HashMap.java L630–671](https://android.googlesource.com/platform/libcore/+/de876a01b29230b877c9f408348c38a90ee724a5/ojluni/src/main/java/java/util/HashMap.java#630)

```java
    Node<K,V> newNode(int hash, K key, V value, Node<K,V> next) {
        return new Node<>(hash, key, value, next);
    }
```

> 출처: [HashMap.java L1906–1908](https://android.googlesource.com/platform/libcore/+/de876a01b29230b877c9f408348c38a90ee724a5/ojluni/src/main/java/java/util/HashMap.java#1906)

**한 줄에 생기는 객체 수는 상황마다 다르다** (키가 캐시 범위 밖일 때)

| 상황 | 새로 생기는 객체 |
|---|---|
| 맵에 처음 `put` | Integer + Node + `Node[16]` 테이블 |
| 새 키 `put` | Integer + Node (임계값을 넘으면 더 큰 테이블도) |
| 이미 있는 키 `put` | **Integer만.** 저장되지 않으므로 바로 쓰레기가 된다 |
| `get` / `containsKey` / `remove` | Integer만 (조회용 임시 객체) |

Node는 새 키일 때만 생기지만, Integer는 HashMap을 어떻게 쓰든 **호출마다** 생긴다. SparseArray는 이 경우 모두 키 객체가 생기지 않는다.

> 직접 확인하기
> - **소스 이동**: SDK Manager에서 *Sources for Android 36* 설치 후 `AutoBoxingExample.kt`의 `map[1042] = "A"`에서 Cmd+B → Android용 HashMap.java로 이동한다.
> - **디버거**: `오토박싱학습테스트`의 `putToHashMap(hashMap)` 줄에 브레이크포인트 → Step Into(F7)를 반복하면 `valueOf → put → hash → hashCode → putVal → newNode` 순으로 들어간다. 그다음 Variables 창에서 `hashMap` 우클릭 → **View as → Object**로 바꾸고 `table → [인덱스] → HashMap$Node → key = Integer@…`를 펼치면 Node 안에 Integer 객체가 따로 있는 것이 보인다 (기본 표시는 "키=값" 목록이라 내부가 안 보인다).
> - **Profiler 할당 기록**: Profiler: Run 'app' as debuggable → Track Memory Consumption (Java/Kotlin Allocations) → 앱 카드 ①의 측정하기 → Arrange by Callstack에서 `hashMapPutNewKeys`를 펼친다. N = 1,000이면 `valueOf → Integer` 1,000개(16,000 B), `put → putVal → newNode → HashMap$Node` 1,000개(24,000 B), `resize → HashMap$Node[]` 8개(16,448 B)가 찍힌다. 순서는 README의 "Profiler로 확인하기" 참고.

#### 조회 경로: 객체를 여러 번 건너뛴다

```java
    final Node<K,V> getNode(Object key) {
        Node<K,V>[] tab; Node<K,V> first, e; int n, hash; K k;
        if ((tab = table) != null && (n = tab.length) > 0 &&
            (first = tab[(n - 1) & (hash = hash(key))]) != null) {
            if (first.hash == hash && // always check first node
                ((k = first.key) == key || (key != null && key.equals(k))))
                return first;
            if ((e = first.next) != null) {
                if (first instanceof TreeNode)
                    return ((TreeNode<K,V>)first).getTreeNode(hash, key);
                do {
                    if (e.hash == hash &&
                        ((k = e.key) == key || (key != null && key.equals(k))))
                        return e;
                } while ((e = e.next) != null);
            }
        }
        return null;
    }
```

> 출처: [HashMap.java L572–590](https://android.googlesource.com/platform/libcore/+/de876a01b29230b877c9f408348c38a90ee724a5/ojluni/src/main/java/java/util/HashMap.java#572)

`map[1058]`을 찾는 경로는 이렇다.

```
키 박싱 → table → table[2] → Node(1042) → hash 다름 → next → Node(1058)
       → hash 같음 → key.equals() 위해 Integer(1058) 읽기 → value
```

이 객체들은 힙에 흩어져 있어서 따라갈 때마다 CPU 캐시를 놓치기 쉽다(pointer chasing). SparseArray의 이진 탐색은 연속된 `int[]` 안에서만 움직여 캐시 친화적이다. 그래서 항목이 적을 때는 O(log n)인데도 HashMap과 속도 차이가 크지 않다.

#### 그 밖의 동작

- **확장**: size > 용량 × 0.75면 테이블을 두 배로 늘리고 모든 Node 재배치
- **remove**: Node를 체인에서 떼어냄 → Node와 Integer는 GC 대상

#### SparseArray는 엔트리를 어떻게 표현하나

엔트리 객체가 없다. **같은 인덱스**라는 약속으로 키와 값을 짝짓는다.

```
mKeys   int[]    : [ 1042 ][ 1058 ][ 2087 ]   ← 숫자가 칸에 그대로
mValues Object[] : [  ●   ][  ●   ][  ●   ]   ← 값 참조
                      ▼       ▼       ▼
                     "A"     "C"     "B"
```

엔트리 i는 `mKeys[i]`와 `mValues[i]`의 쌍일 뿐이다. Node 헤더, `hash`, `next`, 박싱된 Integer가 모두 필요 없고, 정렬해서 서로 다른 칸에 넣으므로 충돌도 없다.

> "Node 객체의 배열에 저장한다"를 정확히 풀면 **"엔트리마다 Node 객체를 만들고, `Node[]` 배열의 각 칸(버킷)이 그 Node 연결 리스트의 첫 번째를 가리킨다"**이다.
> SparseArray가 아끼는 메모리가 바로 이 Node 객체, 박싱된 Integer, 빈 버킷이다.

### SparseArray

필드 선언 ([SparseArray.java L59–67](https://android.googlesource.com/platform/frameworks/base/+/1cdfff555f4a21f71ccc978290e2e212e2f8b168/core/java/android/util/SparseArray.java#59)): `DELETED`(삭제 표시용 객체), `mGarbage`, `int[] mKeys`, `Object[] mValues`, `int mSize`.

#### 생성: Android 14부터 빈 배열로 시작

```java
    public SparseArray() {
        this(0);
    }
```

> 출처: [SparseArray.java L72–74](https://android.googlesource.com/platform/frameworks/base/+/1cdfff555f4a21f71ccc978290e2e212e2f8b168/core/java/android/util/SparseArray.java#72)

```java
    public SparseArray(int initialCapacity) {
        if (initialCapacity == 0) {
            mKeys = EmptyArray.INT;
            mValues = EmptyArray.OBJECT;
        } else {
            mValues = ArrayUtils.newUnpaddedObjectArray(initialCapacity);
            mKeys = new int[mValues.length];
        }
        mSize = 0;
    }
```

> 출처: [SparseArray.java L83–92](https://android.googlesource.com/platform/frameworks/base/+/1cdfff555f4a21f71ccc978290e2e212e2f8b168/core/java/android/util/SparseArray.java#83)

기본 생성자는 `this(0)`이라 배열을 새로 만들지 않고 모든 인스턴스가 함께 쓰는 `EmptyArray.INT`, `EmptyArray.OBJECT`를 가리킨다. 첫 `put`에서 `growSize(0) = 8`칸을 요청하고, `newUnpaddedIntArray`가 8B 올림으로 남는 칸까지 채워 9칸이 된다.
Android 13(`android-13.0.0_r1`)까지는 `this(10)`이라 생성하자마자 11칸 배열 두 개를 만들었다. `SparseIntArray`도 같은 시점에 `this(10)`에서 `this(0)`으로 바뀌었다.
이 레포의 `메모리할당학습테스트`(`SparseArray 기본 생성자는 Android 14부터 빈 배열로 시작한다`)가 기기에서 이 차이를 확인한다.

#### get: 이진 탐색 한 번

```java
    public E get(int key, E valueIfKeyNotFound) {
        int i = ContainerHelpers.binarySearch(mKeys, mSize, key);

        if (i < 0 || mValues[i] == DELETED) {
            return valueIfKeyNotFound;
        } else {
            return (E) mValues[i];
        }
    }
```

> 출처: [SparseArray.java L132–140](https://android.googlesource.com/platform/frameworks/base/+/1cdfff555f4a21f71ccc978290e2e212e2f8b168/core/java/android/util/SparseArray.java#132)

#### binarySearch: 못 찾으면 ~lo

```java
    static int binarySearch(int[] array, int size, int value) {
        int lo = 0;
        int hi = size - 1;

        while (lo <= hi) {
            final int mid = (lo + hi) >>> 1;
            final int midVal = array[mid];

            if (midVal < value) {
                lo = mid + 1;
            } else if (midVal > value) {
                hi = mid - 1;
            } else {
                return mid;  // value found
            }
        }
        return ~lo;  // value not present
    }
```

> 출처: [ContainerHelpers.java L23–40](https://android.googlesource.com/platform/frameworks/base/+/1cdfff555f4a21f71ccc978290e2e212e2f8b168/core/java/android/util/ContainerHelpers.java#23)

못 찾으면 `~lo`(비트 반전)를 돌려준다. 항상 음수라서 "없음"을 표시하면서 동시에 들어갈 자리도 알려 준다. `~(-3) = 2`처럼 다시 뒤집으면 삽입 위치가 나온다.

#### put: 찾고, 재사용하고, 밀어 넣기

```java
    public void put(int key, E value) {
        int i = ContainerHelpers.binarySearch(mKeys, mSize, key);

        if (i >= 0) {
            mValues[i] = value;
        } else {
            i = ~i;

            if (i < mSize && mValues[i] == DELETED) {
                mKeys[i] = key;
                mValues[i] = value;
                return;
            }

            if (mGarbage && mSize >= mKeys.length) {
                gc();

                // Search again because indices may have changed.
                i = ~ContainerHelpers.binarySearch(mKeys, mSize, key);
            }

            mKeys = GrowingArrayUtils.insert(mKeys, mSize, i, key);
            mValues = GrowingArrayUtils.insert(mValues, mSize, i, value);
            mSize++;
        }
    }
```

> 출처: [SparseArray.java L258–283](https://android.googlesource.com/platform/frameworks/base/+/1cdfff555f4a21f71ccc978290e2e212e2f8b168/core/java/android/util/SparseArray.java#258)

1. 키가 있으면 값만 교체
2. 없으면 `i = ~i`로 삽입 위치를 얻고, 그 자리가 `DELETED`면 덮어써서 재사용
3. 배열이 꽉 찼는데 `DELETED`가 있으면 늘리기 전에 `gc()`로 압축하고 다시 탐색
4. `GrowingArrayUtils.insert`로 끼워 넣기

```java
    public static int[] insert(int[] array, int currentSize, int index, int element) {
        assert currentSize <= array.length;

        if (currentSize + 1 <= array.length) {
            System.arraycopy(array, index, array, index + 1, currentSize - index);
            array[index] = element;
            return array;
        }

        int[] newArray = ArrayUtils.newUnpaddedIntArray(growSize(currentSize));
        System.arraycopy(array, 0, newArray, 0, index);
        newArray[index] = element;
        System.arraycopy(array, index, newArray, index + 1, array.length - index);
        return newArray;
    }
```

> 출처: [GrowingArrayUtils.java L152–166](https://android.googlesource.com/platform/frameworks/base/+/1cdfff555f4a21f71ccc978290e2e212e2f8b168/core/java/com/android/internal/util/GrowingArrayUtils.java#152)

```java
    public static int growSize(int currentSize) {
        return currentSize <= 4 ? 8 : currentSize * 2;
    }
```

> 출처: [GrowingArrayUtils.java L211–213](https://android.googlesource.com/platform/frameworks/base/+/1cdfff555f4a21f71ccc978290e2e212e2f8b168/core/java/com/android/internal/util/GrowingArrayUtils.java#211)

자리가 있으면 `System.arraycopy`로 index 뒤를 한 칸씩 밀고(O(n)), 없으면 두 배 크기 새 배열에 복사한다. `newUnpaddedIntArray`는 8B 올림으로 남는 칸까지 길이로 쓰기 때문에 8칸을 요청하면 9칸, 18칸을 요청하면 19칸이 된다. 키 순서를 유지해야 이진 탐색이 성립하기 때문이다.

#### delete: 표시만 하고 미루기

```java
    public void delete(int key) {
        int i = ContainerHelpers.binarySearch(mKeys, mSize, key);

        if (i >= 0) {
            if (mValues[i] != DELETED) {
                mValues[i] = DELETED;
                mGarbage = true;
            }
        }
    }
```

> 출처: [SparseArray.java L145–154](https://android.googlesource.com/platform/frameworks/base/+/1cdfff555f4a21f71ccc978290e2e212e2f8b168/core/java/android/util/SparseArray.java#145)

삭제할 때마다 배열을 당기면 매번 O(n)이다. 그래서 자리만 비워 두고, 인덱스가 필요한 `size()`, `keyAt()`, `valueAt()` 호출이 올 때 `gc()`로 한 번에 압축한다. `remove(key)`는 `delete(key)`를 그대로 호출한다.

#### gc: DELETED를 건너뛰며 앞으로 당기기

```java
    private void gc() {
        // Log.e("SparseArray", "gc start with " + mSize);

        int n = mSize;
        int o = 0;
        int[] keys = mKeys;
        Object[] values = mValues;

        for (int i = 0; i < n; i++) {
            Object val = values[i];

            if (val != DELETED) {
                if (i != o) {
                    keys[o] = keys[i];
                    values[o] = val;
                    values[i] = null;
                }

                o++;
            }
        }

        mGarbage = false;
        mSize = o;

        // Log.e("SparseArray", "gc end with " + mSize);
    }
```

> 출처: [SparseArray.java L217–243](https://android.googlesource.com/platform/frameworks/base/+/1cdfff555f4a21f71ccc978290e2e212e2f8b168/core/java/android/util/SparseArray.java#217)

배열을 새로 만들지 않고 제자리에서 당기므로 **용량은 줄지 않는다**. 이 `gc()`는 가비지 컬렉터와 무관한 SparseArray 내부 메서드다.

### 예시 시나리오 (초기 키: 7, 42, 108, 900, 3000)

| 연산 | HashMap | SparseArray |
|---|---|---|
| `put 23` | 23 & 15 = 7번 버킷 → Node(7)과 충돌 → 체인 끝에 연결 | 이진 탐색 → 삽입 위치 1 → 뒤 4칸 밀기 |
| `put 5` | 5번 버킷에 새 Node | 삽입 위치 0 → **전부(5칸) 밀기** |
| `remove 42` | Node 떼어냄 | `mValues[1] = DELETED`, 이동 0칸 |
| 이어서 `put 30` | 14번 버킷에 새 Node | 삽입 위치 1이 DELETED → **덮어써서 재사용**, 이동 0칸 |
| `get 900` | 4번 버킷으로 바로 이동 | mid=2(108) → mid=3(900), 2번 비교로 발견 |

---

## 6. 위치를 계산할 수는 없을까?

### Q. 전체 원소 개수를 알면 위치를 계산할 수 있지 않나?

없다. 정렬된 배열에서 키 k의 인덱스는 **k보다 작은 키의 개수**다. 이것은 개수 n이 아니라 다른 키들의 실제 값에 달려 있다.

| 키 집합 (n = 3) | 17의 위치 | 3990의 위치 |
|---|---|---|
| {3, 17, 4000} | 인덱스 1 | 없음 |
| {3, 3990, 4000} | 없음 | 인덱스 1 |

개수, 최솟값, 최댓값이 같아도 가운데 칸의 값은 열어 봐야 안다.

계산이 가능한 경우:

- **키가 규칙적일 때**: 연속(0, 1, 2…)이거나 간격이 일정하면 `index = (k - min) / step`. 하지만 그런 데이터면 그냥 배열을 쓰면 된다.
- **보간 탐색**: 키가 고르게 퍼져 있다고 보고 비율로 위치를 추정. 고르면 평균 O(log log n)이지만 치우치면 최악 O(n). SparseArray는 분포와 무관하게 O(log n)을 보장하는 이진 탐색을 택했다.
- **해싱 (HashMap 방식)**: 키 하나만으로 `hash & (n-1)` 계산. 대신 칸을 넉넉히 잡고(load factor 0.75) 충돌을 체인으로 처리한다. 즉 **위치를 계산하는 대가가 빈 칸과 Node 객체**다.

### Q. 4000이 최댓값이라고 판별되면 위치를 계산할 수 있지 않나?

**양 끝은 된다.** 정렬된 배열이니 최댓값은 정의상 `size - 1`, 최솟값은 `0`에 있다. `mKeys[size - 1]`과 한 번만 비교하면 O(1)로 판별된다. AOSP의 `append()`가 바로 이 최적화다.

```java
    public void append(int key, E value) {
        if (mSize != 0 && key <= mKeys[mSize - 1]) {
            put(key, value);
            return;
        }

        if (mGarbage && mSize >= mKeys.length) {
            gc();
        }

        mKeys = GrowingArrayUtils.append(mKeys, mSize, key);
        mValues = GrowingArrayUtils.append(mValues, mSize, value);
        mSize++;
    }
```

> 출처: [SparseArray.java L463–476](https://android.googlesource.com/platform/frameworks/base/+/1cdfff555f4a21f71ccc978290e2e212e2f8b168/core/java/android/util/SparseArray.java#463)

- 마지막 키보다 크지 않으면 → `put()` (일반 경로, 이진 탐색)
- 마지막 키보다 크면 → 탐색 없이 끝에 붙임: O(1) ([GrowingArrayUtils.append L63–73](https://android.googlesource.com/platform/frameworks/base/+/1cdfff555f4a21f71ccc978290e2e212e2f8b168/core/java/com/android/internal/util/GrowingArrayUtils.java#63))

키를 오름차순으로 넣는다면 `put` 대신 `append`를 쓰면 이진 탐색과 배열 이동을 모두 건너뛴다. 단, `get()`에는 이런 지름길이 없어서 최댓값 조회도 이진 탐색을 한다.

**중간 키는 안 된다.**

- size가 3이면 사이에 칸이 1개뿐이라 "인덱스 1이거나 없음"으로 좁혀져서 계산되는 것처럼 보인다. 이진 탐색도 첫 비교를 인덱스 1에서 하므로 결과는 같다.
- size가 1,000이면 3과 4000 사이에 998칸이 있고, 17이 어디인지는 다른 키를 봐야 안다.
- 비율 추정은 틀릴 수 있다. 17 → `(17-3)/(4000-3) × 2 ≈ 0.007` → 인덱스 0으로 추정하지만 실제는 1.

> 양 끝은 위치가 정의상 정해져 있어서 O(1)로 처리할 수 있다(`append`).
> 중간 키의 위치는 다른 키들의 값에 달려 있어서 찾아야 하고, 그래서 이진 탐색을 쓴다.

---

## 7. 연산별 비교

| 항목 | `HashMap<Int, V>` | `SparseArray<V>` |
|---|---|---|
| 조회 | 평균 O(1). 충돌하면 체인을 따라감 | O(log n) 이진 탐색 |
| 새 키 삽입 | 평균 O(1). 용량 × 0.75 초과 시 테이블 두 배 + 재배치 | O(n). 중간 삽입은 뒤쪽을 밂. 오름차순 삽입은 끝에 붙어 빠름 |
| 삭제 | Node 떼어냄. Node·Integer는 GC 대상 | DELETED 표시 후 나중에 `gc()` 압축. 용량 유지 |
| 항목당 객체 | Node 1 + Integer 1 (캐시 밖일 때) | 없음 |
| 순회 순서 | 보장 안 됨 | 키 오름차순. `keyAt(i)`/`valueAt(i)`로 반복자 할당 없이 순회 |
| null 키 | 1개 허용 | 불가 (키가 int) |
| 값으로 찾기 | `containsValue`는 `equals` 비교 | `indexOfValue`는 `==` 참조 비교. 내용이 같은 다른 객체는 못 찾음 |
| 인터페이스 | `Map` 구현, 컬렉션 API 호환 | `Map` 아님. androidx.core의 `forEach`, `keyIterator` 등 일부 확장만 |
| 단위 테스트 | 순수 JVM에서 동작 | `android.util`이라 로컬 테스트는 Robolectric 필요 |

공식 문서: 수백 개 정도까지는 HashMap과의 성능 차이가 50% 미만으로 크지 않다.

---

## 8. 비슷한 자료구조

| 클래스 | 키 → 값 | 특징 |
|---|---|---|
| `SparseIntArray` | int → int | 값도 `int[]`. 박싱 전혀 없음 |
| `SparseBooleanArray` | int → boolean | 체크된 position 같은 상태 저장 |
| `SparseLongArray` | int → long | 값이 long일 때 |
| `LongSparseArray` | long → V | 키가 long일 때 (DB id 등). androidx 버전도 있음 |
| `SparseArrayCompat` | int → V | androidx.collection. 순수 Kotlin 구현이라 JVM 테스트에서 그대로 사용 |
| `ArrayMap` | K → V | 키 타입 제한 없음. 키 해시를 정렬된 `int[]`에 두고 이진 탐색. 정수 키는 여전히 박싱 |
| `IntObjectMap` | int → V | androidx.collection 1.4+. 박싱 없는 오픈 어드레싱 해시 테이블, 평균 O(1) |

---

## 9. 무엇을 쓸지 고르는 순서

1. **키가 int나 long인가?** 아니면 `HashMap`. 수백 개 이하로 작으면 `ArrayMap`도 후보.
2. **다른 API에 `Map`으로 넘겨야 하나?** 그렇다면 `HashMap`.
3. **항목이 수천 개를 넘거나 중간 삽입·삭제가 아주 잦나?** 그렇다면 `HashMap`이나 `IntObjectMap`.
4. **값도 원시 타입인가?** int → `SparseIntArray`, boolean → `SparseBooleanArray`, long → `SparseLongArray`. 키가 long이면 `LongSparseArray`.
5. **순수 JVM 단위 테스트에서도 쓰나?** 그렇다면 `SparseArrayCompat`, 아니면 `SparseArray`.

---

## 10. 실전 질문 답변

**Q. HashMap 대신 SparseArray를 사용하는 것이 어떤 시나리오에서 더 효율적이며, 성능 및 사용성 측면에서 트레이드오프는 무엇인가요?**

키가 `int`이고, 항목이 수백~수천 개 이하이며, 조회 속도보다 메모리가 중요한 곳에서 유리하다.

- **잘 맞는 곳**: View ID → 객체 캐시, 어댑터 position → 상태, 오래 살아 있는 작은 매핑.
  값도 원시 타입이면 `SparseIntArray` 등으로 값 박싱까지 없앤다.
- **이유**: HashMap은 항목마다 `Node`(24B)와 박싱된 `Integer`(16B)를 만들지만, SparseArray는 `int[]`와 `Object[]` 두 배열에 담아 항목당 객체가 없다.
  1,000개 기준 약 47KB vs 10KB (Android 14 이상). 조회·포함 확인·삭제처럼 키를 넘길 때마다 생기는 박싱 임시 객체도 없다.
- **성능 트레이드오프**: 조회 O(log n), 중간 삽입 O(n). 수만 개 이상이거나 삽입·삭제가 잦으면 HashMap이 낫다.
  오름차순 삽입은 `append()`로 O(1), 삭제는 DELETED 표시로 미뤄서 비용을 줄인다.
- **사용성 트레이드오프**: `Map` 인터페이스가 아니라 컬렉션 API를 바로 쓸 수 없고, `android.util` 의존이라 JVM 단위 테스트에서는 `SparseArrayCompat`이나 Robolectric이 필요하다.
  `indexOfValue`는 `==` 비교라는 점도 주의. 대신 순회 순서가 키 오름차순으로 보장된다.

---

참고

코드 출처 (고정 커밋)
- `android.util.SparseArray`, `ContainerHelpers`, `com.android.internal.util.GrowingArrayUtils`: AOSP `platform/frameworks/base` 커밋 [`1cdfff5`](https://android.googlesource.com/platform/frameworks/base/+/1cdfff555f4a21f71ccc978290e2e212e2f8b168)
- `java.util.HashMap`, `java.lang.Integer` (Android, OpenJDK 기반): AOSP `platform/libcore` 커밋 [`de876a0`](https://android.googlesource.com/platform/libcore/+/de876a01b29230b877c9f408348c38a90ee724a5)
- 두 커밋 모두 2026-10-14 기준 `main` 브랜치 최신. "설명용 코드"라고 표시한 블록은 원본이 아니라 이해를 돕기 위해 직접 쓴 코드다.

문서
- [SparseArray 문서](https://developer.android.com/reference/android/util/SparseArray)
- [SparseArray 소스 (AOSP)](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/core/java/android/util/SparseArray.java)
- [Android HashMap 소스](https://android.googlesource.com/platform/libcore/+/refs/heads/main/ojluni/src/main/java/java/util/HashMap.java)
- [androidx.collection](https://developer.android.com/reference/kotlin/androidx/collection/package-summary)
- [힙 덤프 분석](https://developer.android.com/studio/profile/capture-heap-dump)
