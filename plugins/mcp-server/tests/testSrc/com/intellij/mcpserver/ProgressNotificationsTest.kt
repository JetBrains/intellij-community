package com.intellij.mcpserver

import com.intellij.mcpserver.annotations.McpTool
import com.intellij.openapi.progress.coroutineToIndicator
import com.intellij.openapi.util.registry.Registry
import com.intellij.platform.util.progress.reportProgressScope
import com.intellij.platform.util.progress.withProgressText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class ProgressNotificationsTest : McpToolsetTestBase() {
  companion object {
    private const val PROGRESS_NOTIFICATION_INTERVAL_REGISTRY_KEY = "mcp.server.progress.notification.interval.ms"
    private const val TEST_INTERVAL_MS = 100L
    private const val MIN_ACCEPTABLE_INTERVAL_MS = 70L
    private val TEST_PROGRESS_DELAY = 350.milliseconds
  }

  @BeforeEach
  fun setProgressNotificationInterval() {
    Registry.get(PROGRESS_NOTIFICATION_INTERVAL_REGISTRY_KEY).setValue(TEST_INTERVAL_MS.toInt())
  }

  @AfterEach
  fun clearProgressNotificationInterval() {
    Registry.get(PROGRESS_NOTIFICATION_INTERVAL_REGISTRY_KEY).resetToDefault()
  }

  @Test
  fun progress_notifications_for_inline_progress_are_throttled_and_keep_alive() {
    runBlocking(Dispatchers.Default) {
      withRegisteredTestTools(this@ProgressNotificationsTest::flow_inline_progress) {
        val call = callToolWithProgress(toolName = "flow_inline_progress", timeout = 10.seconds)

        assertThat(call.result.textContent.text).isEqualTo("inline-done")
        assertThat(call.progressEvents).isNotEmpty()
        assertThrottled(call.progressEvents)
        assertKeepAlive(call.progressEvents)
        assertThat(call.progressEvents.map { it.progress.message })
          .anySatisfy { message -> assertThat(message).contains("Inline progress") }
        assertThat(call.progressEvents.map { it.progress.message })
          .anySatisfy { message -> assertThat(message).contains("phase 2") }
      }
    }
  }

  @Test
  fun progress_notifications_for_background_progress_are_reported_via_task_updates() {
    runBlocking(Dispatchers.Default) {
      withRegisteredTestTools(this@ProgressNotificationsTest::flow_background_progress) {
        val call = callToolWithProgress(toolName = "flow_background_progress", timeout = 10.seconds)

        assertThat(call.result.textContent.text).isEqualTo("background-done")
        assertThat(call.progressEvents).isNotEmpty()
        assertThrottled(call.progressEvents)
        assertKeepAlive(call.progressEvents)
        assertThat(call.progressEvents.map { it.progress.progress })
          .anySatisfy { progress -> assertThat(progress).isGreaterThan(0.0) }
        assertThat(call.progressEvents.map { it.progress.total })
          .anySatisfy { total -> assertThat(total).isEqualTo(1.0) }
      }
    }
  }

  @McpTool(title = "Flow inline progress")
  suspend fun flow_inline_progress(): String {
    withProgressText("Inline progress") {
      reportProgressScope(size = 2) { reporter ->
        reporter.itemStep("phase 1") {
          coroutineToIndicator { indicator ->
            indicator.fraction = 1.0
          }
          delay(TEST_PROGRESS_DELAY)
        }
        reporter.itemStep("phase 2") {
          coroutineToIndicator { indicator ->
            indicator.fraction = 1.0
          }
          delay(TEST_PROGRESS_DELAY)
        }
      }
    }
    return "inline-done"
  }

  @McpTool(title = "Flow background progress")
  suspend fun flow_background_progress(): String {
    com.intellij.platform.ide.progress.withBackgroundProgress(currentCoroutineContext().project, "Background progress") {
      reportProgressScope(size = 2) { reporter ->
        reporter.itemStep {
          coroutineToIndicator { indicator ->
            indicator.text = "Background progress"
            indicator.text2 = "phase 1"
            indicator.fraction = 1.0
          }
          delay(TEST_PROGRESS_DELAY)
        }
        reporter.itemStep {
          coroutineToIndicator { indicator ->
            indicator.text = "Background progress"
            indicator.text2 = "phase 2"
            indicator.fraction = 1.0
          }
          delay(TEST_PROGRESS_DELAY)
        }
      }
    }
    return "background-done"
  }

  private fun assertThrottled(progressEvents: List<ObservedProgress>) {
    assertThat(progressEvents.size).isGreaterThanOrEqualTo(3)
    progressEvents.zipWithNext()
      .dropLast(1)
      .forEach { (previous, next) ->
      val deltaMillis = (next.receivedAtNanos - previous.receivedAtNanos) / 1_000_000
      assertThat(deltaMillis).isGreaterThanOrEqualTo(MIN_ACCEPTABLE_INTERVAL_MS)
      }
  }

  private fun assertKeepAlive(progressEvents: List<ObservedProgress>) {
    val hasRepeatedPayload = progressEvents.zipWithNext().any { (previous, next) ->
      previous.progress.progress == next.progress.progress &&
      previous.progress.total == next.progress.total &&
      previous.progress.message == next.progress.message
    }
    assertThat(hasRepeatedPayload).isTrue()
  }
}
