package com.example.sparsearraylab

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.view.WindowInsets
import android.widget.Button
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.TextView
import java.util.concurrent.Executors

/** md의 메모리 설명을 할당 카운터와 Profiler로 확인하는 화면. */
class MainActivity : Activity() {
    private val executor = Executors.newSingleThreadExecutor()
    private val counts = listOf(10, 100, 1_000, 10_000)
    private var n = 1_000
    private var running = false

    private lateinit var boxingButton: Button
    private lateinit var holdButton: Button
    private lateinit var releaseButton: Button
    private lateinit var deleteButton: Button
    private lateinit var holdState: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val padding = dp(16)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padding, padding, padding, padding)
        }
        content.setOnApplyWindowInsetsListener { view, insets ->
            val bars = insets.getInsets(WindowInsets.Type.systemBars())
            view.setPadding(padding + bars.left, padding + bars.top,
                padding + bars.right, padding + bars.bottom)
            insets
        }
        setContentView(ScrollView(this).apply {
            setBackgroundColor(Color.rgb(243, 245, 249))
            addView(content)
        })
        content.requestApplyInsets()

        addText(content, "SparseArray vs HashMap 메모리 관찰", 23f, bold = true)
        addText(content, "버튼을 누르면 실제로 만들어진 객체 수와 바이트를 세어, 문서의 계산식으로 구한 기대값과 나란히 보여 줍니다.", 14f)
        val startLength = ExpectedMemory.defaultStartLength
        addText(content, "이 기기: Android API ${Build.VERSION.SDK_INT} · 페이지 ${ExpectedMemory.pageSize / 1024} KB\n" +
            "SparseArray() 시작 길이 ${startLength}칸 " +
            (if (startLength == 0) "(Android 14부터 빈 배열로 시작)" else "(Android 13 이하는 10칸 요청 → 11칸)") +
            "\n12 KB 이상인 int[]는 페이지 단위로 잡혀, N = 10,000에서는 int[]가 같은 길이의 Object[]보다 커집니다. 기대값에 반영되어 있습니다.", 13f)
        addCountSelector(content)

        val boxing = addCard(content,
            "① 키 하나 넣을 때 객체가 몇 개 생길까?",
            "HashMap<Int, V>는 키를 Integer로 박싱하고 새 키마다 Node를 만듭니다. SparseArray는 int 키를 배열에 그대로 저장합니다.",
            "HashMap\n새 키 put   → Integer + Node\n같은 키 put → Integer\nget         → Integer\n\nSparseArray\nput · get   → 키 객체 없음\n배열이 꽉 차면 더 큰 int[]·Object[]만",
            "Profiler로 보기: Track Memory Consumption (Java/Kotlin Allocations) → 버튼 누른 구간 선택 → Arrange by Callstack → hashMapPutNewKeys 등 단계별 함수를 펼칩니다.")
        boxingButton = addButton(boxing.card, "측정하기") {
            run(boxing.result, "N = ${format(n)}") { formatRows(MemoryObservation.boxing(n), showRetained = false) }
        }
        boxing.card.addView(boxing.result)

        val hold = addCard(content,
            "② 컨테이너 하나가 통째로 몇 바이트일까?",
            "네 컨테이너를 N개로 채워 붙잡아 둡니다. 기대 Retained는 컨테이너가 사라지면 함께 사라질 크기입니다. 값 String은 모두가 공유하므로 빠집니다.",
            "HashMap<Int, String>\n  40 + 테이블 + (24 + 16) × N\nSparseArray<String>\n  24 + int[] + Object[]\nHashMap<Int, Int>\n  40 + 테이블 + (24 + 16 + 16) × N\nSparseIntArray\n  24 + int[] + int[]",
            "Profiler로 보기: 붙잡은 상태에서 Analyze Memory Usage (Heap Dump) → HeldContainers 검색 → 인스턴스의 각 필드 Retained Size. " +
                "힙 덤프는 객체 크기를 8 B 정렬 전으로 보여 줄 수 있어(예: Integer 12, SparseArray 21) 기대값보다 조금 작게 나올 수 있습니다. " +
                "측정값은 채우는 동안 만든 전체 할당이라, 확장하며 버린 작은 배열까지 포함되어 Retained보다 큽니다.")
        holdButton = addButton(hold.card, "N개로 채워서 붙잡기") {
            run(hold.result, "N = ${format(n)} · 채우는 동안 할당") { formatRows(MemoryObservation.hold(n), showRetained = true) }
        }
        releaseButton = addButton(hold.card, "붙잡은 컨테이너 놓기") {
            HeldContainers.clear()
            hold.result.text = "놓았습니다. 다음 GC에서 회수됩니다."
            updateButtons()
        }
        holdState = addText(hold.card, "", 13f, bold = true)
        hold.card.addView(hold.result)

        val delete = addCard(content,
            "③ 90%를 지우면 메모리도 줄어들까?",
            "②에서 붙잡은 컨테이너에서 앞쪽 90%를 지웁니다. HashMap의 remove도 키를 박싱합니다. SparseArray는 DELETED 표시 후 gc()로 당기지만 배열을 줄이지 않습니다.",
            "HashMap\n테이블 용량 그대로\nNode·Integer만 사라짐\n\nSparseArray\n[값, 삭제, 삭제, 값, …]\n  ↓ gc()\n[값, 값, 빈칸, 빈칸, …]\n배열 길이 그대로",
            "Profiler로 보기: 지우기 전과 후에 각각 힙 덤프를 떠서 HeldContainers 필드의 Retained Size를 비교합니다.")
        deleteButton = addButton(delete.card, "붙잡은 컨테이너에서 90% 지우기") {
            run(delete.result, "삭제 ${format(MemoryObservation.deleteCount(HeldContainers.keys.size))}개 · 지우는 동안 할당") {
                formatRows(MemoryObservation.deleteMost(), showRetained = true)
            }
        }
        delete.card.addView(delete.result)
        updateButtons()
    }

    private class Card(val card: LinearLayout, val result: TextView)

    private fun addCountSelector(parent: LinearLayout) {
        addText(parent, "항목 수 N", 13f, bold = true)
        val group = RadioGroup(this).apply { orientation = RadioGroup.HORIZONTAL }
        counts.forEach { count ->
            group.addView(RadioButton(this).apply {
                id = count
                text = format(count)
                textSize = 14f
            })
        }
        group.check(n)
        group.setOnCheckedChangeListener { _, checkedId -> n = checkedId }
        parent.addView(group)
    }

    private fun addCard(parent: LinearLayout, question: String, explanation: String, diagram: String, profilerHint: String): Card {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
            background = GradientDrawable().apply {
                setColor(Color.WHITE)
                cornerRadius = dp(12).toFloat()
                setStroke(dp(1), Color.rgb(219, 226, 236))
            }
        }
        parent.addView(card, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(20) })
        addText(card, question, 19f, bold = true)
        addText(card, explanation, 15f)
        addText(card, diagram, 14f).apply {
            typeface = Typeface.MONOSPACE
            setPadding(dp(12), dp(12), dp(12), dp(12))
            setBackgroundColor(Color.rgb(239, 244, 250))
        }
        addText(card, profilerHint, 13f).setTextColor(Color.rgb(76, 91, 111))
        val result = TextView(this).apply {
            text = "아직 실행하지 않았습니다."
            textSize = 13f
            typeface = Typeface.MONOSPACE
            setTextColor(Color.rgb(35, 51, 71))
            setTextIsSelectable(true)
            setPadding(0, dp(8), 0, dp(8))
        }
        return Card(card, result)
    }

    private fun addButton(card: LinearLayout, label: String, onClick: () -> Unit) =
        Button(this).apply {
            text = label
            setOnClickListener { onClick() }
            card.addView(this)
        }

    /** 측정은 백그라운드에서 한다. 할당 카운터는 스레드별이라 화면 그리기 할당이 섞이지 않는다. */
    private fun run(result: TextView, title: String, operation: () -> String) {
        running = true
        updateButtons()
        result.text = "측정하는 중…"
        executor.execute {
            val text = try {
                "$title\n\n${operation()}"
            } catch (exception: Exception) {
                "실행 오류: ${exception.message}"
            }
            runOnUiThread {
                if (isDestroyed) return@runOnUiThread
                result.text = text
                running = false
                updateButtons()
            }
        }
    }

    private fun updateButtons() {
        boxingButton.isEnabled = !running
        holdButton.isEnabled = !running
        releaseButton.isEnabled = !running && HeldContainers.isHeld
        deleteButton.isEnabled = !running && HeldContainers.isHeld && !HeldContainers.deleted
        holdState.text = when {
            !HeldContainers.isHeld -> "붙잡은 컨테이너 없음"
            HeldContainers.deleted -> "붙잡는 중: ${format(HeldContainers.keys.size)}개 중 90% 삭제됨"
            else -> "붙잡는 중: 각 ${format(HeldContainers.keys.size)}개"
        }
    }

    private fun formatRows(rows: List<ObservationRow>, showRetained: Boolean) = rows.joinToString("\n\n") { row ->
        buildString {
            append(if (row.matches) "✓ " else "✗ ").append(row.label).append('\n')
            append("  측정 ").append(format(row.measured)).append('\n')
            append("  기대 ").append(format(row.expected))
            if (showRetained && row.retainedBytes != null) {
                append("\n  기대 Retained ").append(format(row.retainedBytes)).append(" B")
            }
        }
    } + "\n\n" + if (rows.all { it.matches }) "모든 측정값이 기대값과 일치합니다." else "기대값과 다른 항목이 있습니다(✗)."

    private fun format(allocation: Allocation) = "${format(allocation.count)}개 / ${format(allocation.bytes)} B"

    private fun format(value: Int) = "%,d".format(value)

    private fun addText(parent: LinearLayout, value: String, size: Float, bold: Boolean = false): TextView {
        val view = TextView(this).apply {
            text = value
            textSize = size
            setTextColor(Color.rgb(35, 51, 71))
            if (bold) setTypeface(typeface, Typeface.BOLD)
            setPadding(0, dp(6), 0, dp(6))
        }
        parent.addView(view)
        return view
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    override fun onDestroy() {
        executor.shutdown()
        super.onDestroy()
    }
}
