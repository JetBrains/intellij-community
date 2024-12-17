// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.reworked

import com.intellij.platform.util.coroutines.childScope
import com.intellij.testFramework.DisposableRule
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.RuleChain
import com.intellij.testFramework.common.timeoutRunBlocking
import com.jediterm.terminal.TerminalKeyEncoder
import junit.framework.TestCase.assertEquals
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.plugins.terminal.block.reworked.session.TerminalCloseEvent
import org.jetbrains.plugins.terminal.block.reworked.session.TerminalInputEvent
import org.jetbrains.plugins.terminal.block.reworked.session.TerminalWriteBytesEvent
import org.jetbrains.plugins.terminal.block.reworked.session.output.*
import org.jetbrains.plugins.terminal.reworked.util.TerminalSessionTestUtil
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.awt.event.KeyEvent
import java.nio.file.Path
import kotlin.time.Duration.Companion.seconds

@RunWith(Parameterized::class)
internal class ShellIntegrationTest(private val shellPath: Path) {
  private val projectRule: ProjectRule = ProjectRule()
  private val disposableRule = DisposableRule()

  private val keyEncoder = TerminalKeyEncoder()

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
    val events = startSessionAndCollectOutputEvents { inputChannel ->
      inputChannel.send(TerminalWriteBytesEvent("pwd".toByteArray() + keyEncoder.enterBytes()))
    }

    val shellIntegrationEvents = events.filter { it is TerminalShellIntegrationEvent }
    val expectedEvents = listOf(
      TerminalShellIntegrationInitializedEvent,
      TerminalCommandStartedEvent("pwd"),
      TerminalCommandFinishedEvent("pwd", 0)
    )

    assertEquals(expectedEvents, shellIntegrationEvents)
  }

  @Test
  fun `shell integration should not send command finished event without command started event on Ctrl+C`() = timeoutRunBlocking(30.seconds) {
    val events = startSessionAndCollectOutputEvents { inputChannel ->
      inputChannel.send(TerminalWriteBytesEvent("abcdef".toByteArray()))
      inputChannel.send(TerminalWriteBytesEvent(CTRL_C_BYTES))
    }

    val shellIntegrationEvents = events.filter { it is TerminalShellIntegrationEvent }
    val expectedEvents = listOf(TerminalShellIntegrationInitializedEvent)

    assertEquals(expectedEvents, shellIntegrationEvents)
  }

  private suspend fun startSessionAndCollectOutputEvents(block: suspend (SendChannel<TerminalInputEvent>) -> Unit): List<TerminalOutputEvent> {
    return coroutineScope {
      val session = TerminalSessionTestUtil.startTestTerminalSession(shellPath.toString(), projectRule.project, childScope("TerminalSession"))

      val outputEvents = mutableListOf<TerminalOutputEvent>()
      launch {
        for (events in session.outputChannel) {
          outputEvents.addAll(events)
        }
      }

      block(session.inputChannel)

      delay(1000)
      session.inputChannel.send(TerminalCloseEvent())

      outputEvents
    }
  }

  private fun TerminalKeyEncoder.enterBytes(): ByteArray {
    return getCode(KeyEvent.VK_ENTER, 0)
  }

  private val CTRL_C_BYTES: ByteArray = byteArrayOf(0x03)
}