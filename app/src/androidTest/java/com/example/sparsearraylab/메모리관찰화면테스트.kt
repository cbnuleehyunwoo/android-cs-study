package com.example.sparsearraylab

import android.app.Activity
import android.content.Intent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CompoundButton
import android.widget.TextView
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class 메모리관찰화면테스트 {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @After
    fun releaseHeldContainers() {
        HeldContainers.clear()
    }

    @Test
    fun `세 관찰 카드와 버튼을 보여준다`() {
        val activity = launchActivity()
        try {
            instrumentation.runOnMainSync {
                val text = allText(activity)
                assertTrue(text.contains("객체가 몇 개 생길까"))
                assertTrue(text.contains("통째로 몇 바이트일까"))
                assertTrue(text.contains("90%를 지우면"))
                assertEquals(4, buttons(activity).size)
                assertFalse("붙잡기 전에는 삭제할 수 없다", button(activity, "90% 지우기").isEnabled)
            }
        } finally {
            instrumentation.runOnMainSync { activity.finish() }
        }
    }

    @Test
    fun `측정값이 기대값과 일치한다고 표시한다`() {
        val activity = launchActivity()
        try {
            click(activity, "측정하기")
            click(activity, "N개로 채워서 붙잡기")
            click(activity, "90% 지우기")
            instrumentation.runOnMainSync {
                val text = allText(activity)
                assertEquals(3, Regex("모든 측정값이 기대값과 일치합니다").findAll(text).count())
                assertFalse(text.contains("✗"))
            }
        } finally {
            instrumentation.runOnMainSync { activity.finish() }
        }
    }

    private fun click(activity: Activity, label: String) {
        instrumentation.runOnMainSync { button(activity, label).performClick() }
        // 측정이 끝나면 버튼이 다시 활성화된다.
        val deadline = System.nanoTime() + 10_000_000_000L
        var done = false
        while (!done && System.nanoTime() < deadline) {
            instrumentation.runOnMainSync { done = button(activity, "측정하기").isEnabled }
            if (!done) Thread.sleep(50)
        }
        assertTrue("$label 실행이 끝나야 한다", done)
    }

    private fun launchActivity(): Activity = instrumentation.startActivitySync(
        Intent(instrumentation.targetContext, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)).also { instrumentation.waitForIdleSync() }

    // RadioButton도 Button을 상속하므로 N 선택지는 뺀다.
    private fun buttons(activity: Activity) = descendants(activity.window.decorView)
        .filterIsInstance<Button>().filterNot { it is CompoundButton }

    private fun button(activity: Activity, label: String) = buttons(activity).first { it.text.contains(label) }

    private fun allText(activity: Activity) =
        descendants(activity.window.decorView).filterIsInstance<TextView>().joinToString("\n") { it.text }

    private fun descendants(view: View): List<View> = listOf(view) +
        if (view is ViewGroup) (0 until view.childCount).flatMap { descendants(view.getChildAt(it)) }
        else emptyList()
}
