# SparseArray 학습 앱

`visuals/sparsearray-summary.md`의 메모리 설명(박싱, Node, 배열 확장)을 실제 기기에서 확인하는 앱과 학습 테스트입니다.

## 메모리 관찰 화면

Android Studio에서 실행 구성을 **app**으로 선택하고 Run을 누르세요.
위쪽에서 항목 수 N(10 · 100 · 1,000 · 10,000)을 고르고 카드의 버튼을 누릅니다.
각 작업이 만든 객체 수와 바이트를 **측정값**으로, md의 계산식으로 구한 값을 **기대값**으로 나란히 보여 주고 일치하면 ✓를 붙입니다.

| 카드 | 하는 일 | 확인하는 것 |
|---|---|---|
| ① 키 하나 넣을 때 객체가 몇 개 생길까? | HashMap과 SparseArray에 새 키 put, 같은 키 put, get을 각 N회 | HashMap은 키마다 Integer, 새 키면 Node까지. SparseArray는 꽉 찰 때의 배열뿐 |
| ② 컨테이너 하나가 통째로 몇 바이트일까? | `HashMap<Int, String>`, `SparseArray<String>`, `HashMap<Int, Int>`, `SparseIntArray`를 N개로 채워 붙잡아 둠 | 채우는 동안의 할당과, 남아 있는 크기(기대 Retained) |
| ③ 90%를 지우면 메모리도 줄어들까? | ②의 컨테이너에서 앞쪽 90% 삭제 | HashMap `remove`도 키를 박싱함. 지워도 HashMap 테이블과 SparseArray 배열은 줄지 않음 |

소스:
- `MainActivity.kt`: 화면
- `MemoryObservation.kt`: 세 카드의 작업. 단계마다 함수를 나눠 Profiler 콜스택에서 구분됩니다. 붙잡은 컨테이너는 `HeldContainers`에 있습니다.
- `ExpectedMemory.kt`: 기대값 계산식 (md 4장)
- `AllocationCounter.kt`: 측정. ART의 스레드별 할당 카운터(`Debug.startAllocCounting`)로 블록 안에서 만든 객체를 셉니다. 측정은 백그라운드 스레드에서 하므로 화면 그리기 할당이 섞이지 않습니다.

### 기대값이 기기마다 달라지는 곳

- **SparseArray 시작 용량:** `SparseArray()`와 `SparseIntArray()`는 Android 13까지 `this(10)`, Android 14(API 34)부터 `this(0)`입니다.
  14 이상은 빈 배열을 공유해 생성 직후 객체 하나(24 B)뿐이고, 첫 put에서 9칸 배열을 만듭니다.
- **배열 길이:** SparseArray 계열은 `VMRuntime.newUnpaddedArray`로 배열을 만들어 정렬로 남는 칸까지 씁니다. 8칸을 요청하면 48 B가 잡혀 9칸이 됩니다.
- **큰 int 배열:** 12 KB 이상인 원시 타입 배열은 Large Object Space에 페이지 단위로 잡힙니다. `Object[]`는 해당하지 않습니다.
  16 KB 페이지 기기에서는 `int[5118]`(20,484 B)이 32,768 B로 잡히고, 남는 칸까지 채워 8,189칸이 됩니다.
  그래서 N = 10,000에서는 SparseArray의 `int[]`가 `Object[]`보다 커집니다. 화면 위쪽에 기기의 페이지 크기가 나옵니다.
- **디버그 빌드 기준:** 릴리스 빌드는 컴파일러 최적화로 일부 박싱이 사라질 수 있습니다.
- 프로세스에서 처음 실행하는 경로는 측정과 무관한 일회성 할당이 섞여서, 측정 전에 같은 작업을 작은 컨테이너로 한 번 실행해 둡니다(`MemoryObservation.warmUp`).

에뮬레이터(API 37, 16 KB 페이지)에서 N = 10, 100, 1,000, 10,000 모두 측정값과 기대값이 일치했습니다.

## Profiler로 확인하기

### 할당 기록: 어디서 무엇이 생기나 (카드 ①)

1. 툴바의 Profile 드롭다운에서 **Profiler: Run 'app' as debuggable (complete data)** 를 고릅니다.
   할당 기록은 debuggable 빌드에서만 됩니다.
2. Profiler **Home** 탭의 프로세스 목록에서 `com.example.sparsearraylab`(Manifest Configuration이 Debuggable)을 고릅니다.
3. **Track Memory Consumption (Java/Kotlin Allocations)** 를 선택하고 Allocation Tracking은 **Full**로 둡니다.
   **Start profiler task from: Now**로 시작합니다. 앱이 꺼져 있으면 Process start를 고릅니다.
4. 앱에서 카드 ①의 **측정하기**를 누르고, 결과가 나오면 기록을 멈춥니다.
5. 타임라인에서 버튼을 누른 구간을 드래그해 선택합니다. 위쪽 분홍 점이 터치입니다.
6. **Arrange by: Callstack**에서 측정 스레드(`ThreadPoolExecutor` 아래 `MainActivity$$ExternalSyntheticLambda…`)를 펼칩니다.
   검색창에 `PutNewKeys`처럼 함수 이름을 넣으면 찾기 쉽습니다.

N = 1,000일 때 보이는 것:

| 함수 | 트리 | 개수 | Shallow Size |
|---|---|---|---|
| `hashMapPutNewKeys` | `valueOf` → Integer | 1,000 | 16,000 |
| | `put` → `putVal` → `newNode` → `HashMap$Node` | 1,000 | 24,000 |
| | `put` → `putVal` → `resize` → `HashMap$Node[]` | 8 | 16,448 |
| `hashMapPutSameKeys`, `hashMapGetKeys` | `valueOf` → Integer | 각 1,000 | 각 16,000 |
| `sparseArrayPutNewKeys` | `put` → `GrowingArrayUtils.insert` → `int[]`, `Object[]` | 각 8 | 각 10,264 |
| `sparseArrayPutSameKeys`, `sparseArrayGetKeys` | (트리에 없음) | 0 | 0 |

- `valueOf`가 `put`의 자식이 아니라 형제인 것은 박싱이 HashMap 안이 아니라 호출하는 쪽 코드에서 일어난다는 뜻입니다. 바이트코드의 `Integer.valueOf` → `Map.put` 순서와 같습니다.
- 할당이 없는 경로는 트리에 나오지 않습니다. `sparseArrayPutSameKeys`가 안 보이는 것 자체가 박싱이 없다는 증거입니다.
- 1개당 크기는 Instance List의 Shallow Size나 `Shallow Size ÷ Allocations`로 봅니다.
- `Allocations`는 구간 안에서 만든 개수, `Total Count`는 구간 끝에 살아 있는 개수입니다. 같은 키 put과 get의 Integer는 곧 쓰레기가 되어 GC 후 `Total Count`에서 빠집니다.

### 힙 덤프: 컨테이너가 통째로 몇 바이트인가 (카드 ②, ③)

1. 카드 ②의 **N개로 채워서 붙잡기**를 누릅니다. 컨테이너는 **붙잡은 컨테이너 놓기**를 누를 때까지 남아 있습니다.
2. Profiler **Home**에서 **Analyze Memory Usage (Heap Dump)** 를 선택해 시작합니다. debuggable 앱이면 profileable 권장 경고가 뜨는데 **Start anyway**로 진행합니다.
3. 검색창에 `HeldContainers`를 넣고 인스턴스를 클릭해 Fields에서 `hashMap`, `sparseArray`, `intHashMap`, `sparseIntArray`의 **Retained Size**를 봅니다.
4. 카드 ③의 **90% 지우기** 후 힙 덤프를 한 번 더 떠서 비교합니다.

- 기대 Retained는 카드에 나옵니다. 값 String은 모든 컨테이너가 같은 객체를 공유해서 어느 쪽 Retained에도 들어가지 않습니다.
- 힙 덤프는 객체 크기를 8 B 정렬 전으로 보여 줄 수 있어(예: Integer 12, SparseArray 21) 기대값보다 조금 작게 나올 수 있습니다. 자릿수와 비율을 비교하세요.
- 카드의 측정값(채우는 동안 할당)이 Retained보다 큰 것은 확장하면서 버린 작은 테이블과 배열이 포함되기 때문입니다. N = 1,000이면 HashMap은 56,488 B를 할당하고 48,248 B가 남습니다.

## 오토박싱 확인

`app/src/main/java/com/example/sparsearraylab/AutoBoxingExample.kt`에서 Kotlin `Int`가 `Integer`로 박싱되는 곳을 바이트코드로 확인합니다.
함수마다 주석에 실제 바이트코드에서 확인한 호출을 적었습니다.

**Android Studio:** 파일을 연 상태에서 Tools → Kotlin → Show Kotlin Bytecode → `Integer.valueOf` 검색.
Decompile 결과는 디컴파일러가 `Integer.valueOf`를 자동 박싱으로 생략해 보여 줄 수 있으니 바이트코드로 판단하세요.

**터미널:**

```sh
./gradlew :app:compileDebugKotlin
javap -c -p app/build/intermediates/built_in_kotlinc/debug/compileDebugKotlin/classes/com/example/sparsearraylab/AutoBoxingExample.class \
  | grep -E "public final|Integer.valueOf|put|get"
```

`javap`이 PATH에 없다면 Android Studio 내장 JDK(`/Applications/Android Studio.app/Contents/jbr/Contents/Home/bin/javap`)를 사용합니다.
기대 결과 (발췌):

```
public final void putToHashMap(java.util.HashMap<java.lang.Integer, java.lang.String>);
  sipush        1042
  invokestatic  java/lang/Integer.valueOf:(I)Ljava/lang/Integer;
  invokeinterface java/util/Map.put:(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;
public final void putToSparseArray(android.util.SparseArray<java.lang.String>);
  sipush        1042
  invokevirtual android/util/SparseArray.put:(ILjava/lang/Object;)V
```

- HashMap: `Integer.valueOf`로 박싱한 뒤, 타입이 소거된 `Map.put(Object, Object)`를 호출합니다.
- SparseArray: `put(int, Object)`(`I`는 int)라서 박싱이 없습니다.

`app/src/androidTest`의 `오토박싱학습테스트`는 박싱 결과를 실행 중에 확인합니다.
저장된 키의 클래스가 `java.lang.Integer`인지, `-128~127`은 같은 캐시 객체를 재사용하는지, 그 밖의 값은 박싱할 때마다 새 객체가 생기는지 봅니다.
ART의 `Integer` 캐시 상한은 127로 고정되어 있어 기기마다 결과가 같습니다
([Integer.java IntegerCache](https://android.googlesource.com/platform/libcore/+/de876a01b29230b877c9f408348c38a90ee724a5/ojluni/src/main/java/java/lang/Integer.java#1128)).

## SparseArray 권장 경고 예제

HashMap 경고 대상만 모은 별도 파일은 `app/src/main/java/com/example/sparsearraylab/HashMapWarningExample.kt`입니다.
`namesById()`는 `SparseArray<String>`, `scoresById()`는 `SparseIntArray` 권장 패턴입니다.
아래에 설명한 버전 차이 때문에 현재 Gradle Lint에서는 이 두 생성자에 경고가 뜨지 않습니다.

별도 파일 `app/src/main/java/com/example/sparsearraylab/SparseArrayWarningExample.kt`를 엽니다.

- `before()` — 정수 키를 사용하는 `HashMap<Int, String>` 예제
- `after()` — 같은 데이터를 `SparseArray<String>`으로 저장한 예제
- `boxedValuesWarning()` — `SparseArray<Int>()`에 실제 `UseSparseArrays` 경고가 발생하는 예제. 값까지 정수라면 `SparseIntArray`를 권장합니다.

주의: 이 프로젝트의 AGP 9.4.0 / Lint 32.4.0에서는 **HashMap → SparseArray 경고는 발생하지 않습니다**.
설치된 `JavaPerformanceDetector`의 생성자 검사에는 `HashMap` 분기가 없고 `SparseArray` 분기만 있습니다. 검사 구현은 `SparseArray<Int>` / `SparseArray<Boolean>`의 전용 배열 사용을 검사합니다.
HashMap 예제는 이전 검사/IDE 버전에서의 권장 패턴을 비교하기 위해 남겼으며 경고를 억제하지 않았습니다.
정수 키라는 이유만으로 항상 바꿔야 하는 것은 아니며, `Map` 인터페이스가 필요한 경우에는 그대로 대체할 수 없습니다.

```sh
./gradlew :app:lintDebug
```

결과: `app/build/reports/lint-results-debug.html`.
참고: [UseSparseArrays 검사 설명](https://googlesamples.github.io/android-custom-lint-rules/checks/UseSparseArrays.md.html).

## 학습 테스트

`app/src/androidTest`의 테스트를 에뮬레이터나 기기에서 실행합니다.

```sh
./gradlew :app:connectedDebugAndroidTest
```

| 클래스 | 확인하는 것 |
|---|---|
| `SparseArray학습테스트` | put, get, remove, 덮어쓰기, 오름차순 순회, 이진 탐색 위치 |
| `HashMap학습테스트` | 조회, 덮어쓰기, null 반환, 삭제 |
| `오토박싱학습테스트` | 저장된 키가 Integer인지, -128~127 캐시 재사용, 캐시 밖 값은 매번 새 객체 |
| `메모리할당학습테스트` | 할당 카운터로 센 객체 수와 바이트: Integer와 Node, SparseArray 무할당, 기본 생성자의 버전 차이, 지워도 용량 유지, 큰 int 배열의 페이지 단위 할당 |
| `메모리관찰화면테스트` | 세 카드가 보이고 N = 1,000에서 모든 측정값이 기대값과 일치하는지 |

참고: [SparseArray](https://developer.android.com/reference/android/util/SparseArray),
[Android HashMap 소스](https://android.googlesource.com/platform/libcore/+/refs/heads/main/ojluni/src/main/java/java/util/HashMap.java),
[Java/Kotlin 할당 기록](https://developer.android.com/studio/profile/record-java-kotlin-allocations),
[힙 덤프 분석](https://developer.android.com/studio/profile/capture-heap-dump).
