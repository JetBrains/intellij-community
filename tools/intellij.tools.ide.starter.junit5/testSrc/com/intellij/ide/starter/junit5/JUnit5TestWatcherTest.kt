package com.intellij.ide.starter.junit5

import com.intellij.ide.starter.ide.IDETestContext
import com.intellij.ide.starter.junit5.JUnit5TestWatcher.Companion.MISSING_CONTEXT_LOG_MESSAGE
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import java.io.ByteArrayOutputStream
import java.io.PrintStream

class JUnit5TestWatcherTest {
  companion object {
    private const val FIRST_ACTION = "first"
    private const val SECOND_ACTION = "second"
    private const val FAILURE_ACTION = "failure"
    private const val FINISHED_ACTION = "finished"
  }

  private fun mockContext(): IDETestContext = Mockito.mock(IDETestContext::class.java)

  private fun captureStderr(block: () -> Unit): String {
    val original = System.err
    val buffer = ByteArrayOutputStream()
    System.setErr(PrintStream(buffer))
    try {
      block()
    }
    finally {
      System.setErr(original)
    }
    return buffer.toString()
  }

  @Test
  fun `testSuccessful invokes onFinished actions with provided context`() {
    val context = mockContext()
    val invoked = mutableListOf<IDETestContext>()
    val watcher = JUnit5TestWatcher { context }.apply {
      watcherActions.addOnFinishedAction { invoked.add(it) }
    }

    watcher.testSuccessful(null)

    invoked.shouldContainExactly(context)
  }

  @Test
  fun `testSuccessful invokes all registered onFinished actions in order`() {
    val context = mockContext()
    val calls = mutableListOf<String>()
    val watcher = JUnit5TestWatcher { context }.apply {
      watcherActions.addOnFinishedAction { calls.add(FIRST_ACTION) }
      watcherActions.addOnFinishedAction { calls.add(SECOND_ACTION) }
    }

    watcher.testSuccessful(null)

    calls.shouldContainExactly(FIRST_ACTION, SECOND_ACTION)
  }

  @Test
  fun `testSuccessful skips actions and logs an error when context is null`() {
    var invoked = false
    val watcher = JUnit5TestWatcher { null }.apply {
      watcherActions.addOnFinishedAction { invoked = true }
    }

    val stderr = captureStderr { watcher.testSuccessful(null) }

    invoked.shouldBe(false)
    stderr.contains(MISSING_CONTEXT_LOG_MESSAGE).shouldBe(true)
  }

  @Test
  fun `testFailed invokes onFailure actions and then onFinished actions when context is present`() {
    val context = mockContext()
    val calls = mutableListOf<String>()
    val watcher = JUnit5TestWatcher { context }.apply {
      watcherActions.addOnFailureAction { calls.add(FAILURE_ACTION) }
      watcherActions.addOnFinishedAction { calls.add(FINISHED_ACTION) }
    }

    watcher.testFailed(null, RuntimeException("boom"))

    calls.shouldContainExactly(FAILURE_ACTION, FINISHED_ACTION)
  }

  @Test
  fun `testFailed forwards the same context to failure and finished actions`() {
    val context = mockContext()
    val received = mutableListOf<IDETestContext>()
    val watcher = JUnit5TestWatcher { context }.apply {
      watcherActions.addOnFailureAction { received.add(it) }
      watcherActions.addOnFinishedAction { received.add(it) }
    }

    watcher.testFailed(null, null)

    received.shouldContainExactly(context, context)
  }

  @Test
  fun `testFailed skips both action lists and logs an error when context is null`() {
    var failureInvoked = false
    var finishedInvoked = false
    val watcher = JUnit5TestWatcher { null }.apply {
      watcherActions.addOnFailureAction { failureInvoked = true }
      watcherActions.addOnFinishedAction { finishedInvoked = true }
    }

    val stderr = captureStderr { watcher.testFailed(null, RuntimeException("boom")) }

    failureInvoked.shouldBe(false)
    finishedInvoked.shouldBe(false)
    stderr.contains(MISSING_CONTEXT_LOG_MESSAGE).shouldBe(true)
  }

  @Test
  fun `context getter is re-evaluated on each lifecycle call`() {
    val first = mockContext()
    val second = mockContext()
    var current: IDETestContext? = first
    val received = mutableListOf<IDETestContext>()
    val watcher = JUnit5TestWatcher { current }.apply {
      watcherActions.addOnFinishedAction { received.add(it) }
    }

    watcher.testSuccessful(null)
    current = second
    watcher.testSuccessful(null)

    received.shouldContainExactly(first, second)
  }
}
