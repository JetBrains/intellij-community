package com.intellij.ide.starter

import com.intellij.ide.starter.runner.CurrentTestMethod
import com.intellij.ide.starter.runner.TestMethod
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class CurrentTestMethodTest {
  private val added = mutableListOf<(TestMethod?) -> Unit>()

  @AfterEach
  fun tearDown() {
    added.forEach(CurrentTestMethod::removeOnChangeListener)
    added.clear()
    CurrentTestMethod.set(null)
  }

  @Test
  fun `a throwing listener does not keep the later ones from being announced`() {
    val announced = mutableListOf<String>()
    val throwing: (TestMethod?) -> Unit = { method -> if (method != null) throw IllegalStateException("stale listener") }
    val healthy: (TestMethod?) -> Unit = { method -> if (method != null) announced.add("healthy") }
    addListener(throwing)
    addListener(healthy)

    CurrentTestMethod.set(testMethod())
    val failure = assertThrows<IllegalStateException> { CurrentTestMethod.publishToListeners() }

    failure.message shouldBe "stale listener"
    announced shouldBe listOf("healthy")
  }

  @Test
  fun `every failure of an announcement is reported`() {
    addListener { method -> if (method != null) throw IllegalStateException("first") }
    addListener { method -> if (method != null) throw IllegalStateException("second") }

    CurrentTestMethod.set(testMethod())
    val failure = assertThrows<IllegalStateException> { CurrentTestMethod.publishToListeners() }

    failure.message shouldBe "first"
    failure.suppressed.map { it.message } shouldBe listOf("second")
  }

  /** Registration announces immediately, so a listener under test is added with no method remembered. */
  private fun addListener(listener: (TestMethod?) -> Unit) {
    CurrentTestMethod.set(null)
    CurrentTestMethod.addOnChangeListener(listener)
    added.add(listener)
  }

  private fun testMethod(): TestMethod = TestMethod(
    name = "publishing",
    displayName = "publishing",
    testClass = CurrentTestMethodTest::class.java,
  )
}
