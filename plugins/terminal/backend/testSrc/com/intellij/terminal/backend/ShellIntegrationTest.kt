// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.backend

import com.google.common.base.Ascii
import com.intellij.platform.util.coroutines.childScope
import com.intellij.terminal.backend.util.TerminalSessionTestUtil
import com.intellij.terminal.backend.util.TerminalSessionTestUtil.ENTER_BYTES
import com.intellij.terminal.backend.util.TerminalSessionTestUtil.awaitOutputEvent
import com.intellij.terminal.session.*
import com.intellij.testFramework.DisposableRule
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.RuleChain
import com.intellij.testFramework.common.timeoutRunBlocking
import com.jediterm.core.util.TermSize
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.plugins.terminal.LocalTerminalCustomizer
import org.jetbrains.plugins.terminal.reworked.util.ZshPS1Customizer
import org.junit.Assert
import org.junit.Assume
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.nio.file.Path
import kotlin.io.path.name
import kotlin.time.Duration.Companion.seconds

@RunWith(Parameterized::class)
internal class ShellIntegrationTest(private val shellPath: Path) {
  private val projectRule: ProjectRule = ProjectRule()
  private val disposableRule = DisposableRule()

  @Rule
  @JvmField
  val ruleChain: RuleChain = RuleChain(projectRule, disposableRule)

  companion object {
    @JvmStatic
    @Parameterized.Parameters(name = "{0}")
    fun shells(): List<Path> {
      return TerminalSessionTestUtil.getShellPaths()
    }
  }

  @Test
  fun `shell integration send correct events on command invocation`() = timeoutRunBlocking(30.seconds) {
    val events = startSessionAndCollectOutputEvents { session ->
      session.sendInputEvent(TerminalWriteBytesEvent("pwd".toByteArray() + ENTER_BYTES))
    }

    val shellIntegrationEvents = events.filter { it is TerminalShellIntegrationEvent }
    val expectedEvents = listOf(
      TerminalShellIntegrationInitializedEvent,
      TerminalPromptStartedEvent,
      TerminalPromptFinishedEvent,
      TerminalCommandStartedEvent("pwd"),
      TerminalCommandFinishedEvent("pwd", 0),
      TerminalPromptStartedEvent,
      TerminalPromptFinishedEvent,
    )

    assertSameEvents(shellIntegrationEvents, expectedEvents, events)
  }

  @Test
  fun `shell integration should not send command finished event without command started event on Ctrl+C`() = timeoutRunBlocking(30.seconds) {
    val events = startSessionAndCollectOutputEvents { session ->
      session.sendInputEvent(TerminalWriteBytesEvent("abcdef".toByteArray()))
      delay(1000)
      session.sendInputEvent(TerminalWriteBytesEvent(CTRL_C_BYTES))
    }

    val shellIntegrationEvents = events.filter { it is TerminalShellIntegrationEvent }
    val expectedEvents = listOf(
      TerminalShellIntegrationInitializedEvent,
      TerminalPromptStartedEvent,
      TerminalPromptFinishedEvent,
      TerminalPromptStartedEvent,
      TerminalPromptFinishedEvent
    )

    assertSameEvents(shellIntegrationEvents, expectedEvents, events)
  }

  /**
   * This case is specific only for Zsh.
   * By default, in Zsh prompt is not redrawn after showing the completion results.
   * But it is redrawn if completion results occupy the whole screen.
   * It is the exact case we are testing there.
   */
  @Test
  fun `prompt events received after prompt is redrawn because of long completion output`() = timeoutRunBlocking(30.seconds) {
    Assume.assumeTrue(shellPath.toString().contains("zsh"))

    val events = startSessionAndCollectOutputEvents(TermSize(80, 4)) { session ->
      session.sendInputEvent(TerminalWriteBytesEvent("g".toByteArray() + TAB_BYTES))
      // Shell can ask "do you wish to see all N possibilities? (y/n)"
      // Wait for this question and ask `y`
      delay(1000)
      session.sendInputEvent(TerminalWriteBytesEvent("y".toByteArray()))
    }

    val shellIntegrationEvents = events.filter { it is TerminalShellIntegrationEvent }
    val expectedEvents = listOf(
      TerminalShellIntegrationInitializedEvent,
      TerminalPromptStartedEvent,
      TerminalPromptFinishedEvent,
      TerminalPromptStartedEvent,
      TerminalPromptFinishedEvent
    )

    assertSameEvents(shellIntegrationEvents, expectedEvents, events)
  }

  /**
   * This case is specific only for Bash.
   * Be default, in Bash prompt is redrawn after showing of completion items.
   * So, we are testing this case here.
   */
  @Test
  fun `prompt events received after prompt is redrawn because of showing completion items`() = timeoutRunBlocking(30.seconds) {
    Assume.assumeTrue(shellPath.toString().contains("bash"))

    val bindCommand = "bind 'set show-all-if-ambiguous on'"

    val events = startSessionAndCollectOutputEvents(TermSize(80, 100)) { session ->
      // Configure the shell to show completion items on the first Tab key press.
      session.sendInputEvent(TerminalWriteBytesEvent(bindCommand.toByteArray() + ENTER_BYTES))
      delay(1000)
      session.sendInputEvent(TerminalWriteBytesEvent("gi".toByteArray() + TAB_BYTES))
    }

    val shellIntegrationEvents = events.filter { it is TerminalShellIntegrationEvent }
    val expectedEvents = listOf(
      // Initialization
      TerminalShellIntegrationInitializedEvent,
      TerminalPromptStartedEvent,
      TerminalPromptFinishedEvent,
      // Bind command execution
      TerminalCommandStartedEvent(bindCommand),
      TerminalCommandFinishedEvent(bindCommand, 0),
      TerminalPromptStartedEvent,
      TerminalPromptFinishedEvent,
      // Prompt redraw after completion
      TerminalPromptStartedEvent,
      TerminalPromptFinishedEvent
    )

    assertSameEvents(shellIntegrationEvents, expectedEvents, events)
  }

  @Test
  fun `prompt events received after prompt is redrawn because of Ctrl+L`() = timeoutRunBlocking(30.seconds) {
    val events = startSessionAndCollectOutputEvents { session ->
      session.sendInputEvent(TerminalWriteBytesEvent("abcdef".toByteArray()))
      session.sendInputEvent(TerminalWriteBytesEvent(CTRL_L_BYTES))
    }

    val shellIntegrationEvents = events.filter { it is TerminalShellIntegrationEvent }
    val expectedEvents = listOf(
      TerminalShellIntegrationInitializedEvent,
      TerminalPromptStartedEvent,
      TerminalPromptFinishedEvent,
      TerminalPromptStartedEvent,
      TerminalPromptFinishedEvent
    )

    assertSameEvents(shellIntegrationEvents, expectedEvents, events)
  }

  @Test
  fun `zsh integration can change PS1`() = timeoutRunBlocking(30.seconds) {
    Assume.assumeTrue(shellPath.name == "zsh")
    // It's a good idea to configure Zsh with PowerLevel10k.
    val ps1Suffix = "MyCustomPS1Suffix"
    ExtensionTestUtil.maskExtensions(
      LocalTerminalCustomizer.EP_NAME,
      listOf(ZshPS1Customizer(ps1Suffix)),
      disposableRule.disposable
    )
    val events = startSessionAndCollectOutputEvents {}
    val contentUpdatedEvents = events.filterIsInstance<TerminalContentUpdatedEvent>()
    val suffixFound = contentUpdatedEvents.any { it.text.contains(ps1Suffix) }
    Assert.assertTrue(contentUpdatedEvents.joinToString("\n") { it.text }, suffixFound)
  }

  private suspend fun startSessionAndCollectOutputEvents(
    size: TermSize = TermSize(80, 24),
    block: suspend (TerminalSession) -> Unit,
  ): List<TerminalOutputEvent> {
    return coroutineScope {
      val session = TerminalSessionTestUtil.startTestTerminalSession(shellPath.toString(), projectRule.project, childScope("TerminalSession"), size)

      val outputEvents = mutableListOf<TerminalOutputEvent>()
      val eventsCollectionJob = launch {
        val outputFlow = session.getOutputFlow()
        outputFlow.collect { events ->
          outputEvents.addAll(events)
        }
      }

      // Wait for prompt initialization before going further
      session.awaitOutputEvent(TerminalPromptFinishedEvent)

      block(session)

      launch(start = CoroutineStart.UNDISPATCHED) {
        // Block the coroutine scope completion until we receive the termination event.
        session.awaitOutputEvent(TerminalSessionTerminatedEvent)
        eventsCollectionJob.cancel()
      }

      delay(1000) // Wait for the shell to handle input sent in `block`

      session.sendInputEvent(TerminalCloseEvent)

      outputEvents
    }
  }

  private fun assertSameEvents(
    actual: List<TerminalOutputEvent>,
    expected: List<TerminalOutputEvent>,
    eventsToLog: List<TerminalOutputEvent>,
  ) {
    fun List<TerminalOutputEvent>.asString(): String {
      return joinToString("\n")
    }

    val errorMessage = {
      """
        |
        |Expected:
        |${expected.asString()}
        |
        |But was:
        |${actual.asString()}
        |
        |All events:
        |${eventsToLog.asString()}
      """.trimMargin()
    }

    assertThat(actual)
      .overridingErrorMessage(errorMessage)
      .isEqualTo(expected)
  }

  private val CTRL_C_BYTES: ByteArray = byteArrayOf(Ascii.ETX)
  private val CTRL_L_BYTES: ByteArray = byteArrayOf(Ascii.FF)
  private val TAB_BYTES: ByteArray = byteArrayOf(Ascii.HT)
}