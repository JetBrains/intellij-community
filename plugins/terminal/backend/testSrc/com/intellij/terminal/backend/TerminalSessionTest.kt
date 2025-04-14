package com.intellij.terminal.backend

import com.intellij.platform.util.coroutines.childScope
import com.intellij.terminal.backend.util.TerminalSessionTestUtil
import com.intellij.terminal.backend.util.TerminalSessionTestUtil.ENTER_BYTES
import com.intellij.terminal.backend.util.TerminalSessionTestUtil.awaitOutputEvent
import com.intellij.terminal.session.*
import com.intellij.testFramework.DisposableRule
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.RuleChain
import com.intellij.testFramework.common.timeoutRunBlocking
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.nio.file.Path
import kotlin.time.Duration.Companion.seconds

@RunWith(Parameterized::class)
internal class TerminalSessionTest(private val shellPath: Path) {
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
  fun `session emit TerminalSessionTerminatedEvent on exit`() = timeoutRunBlocking(10.seconds) {
    val session = startTerminalSession(childScope("TerminalSession"))

    launch(start = CoroutineStart.UNDISPATCHED) {
      // The test will be finished once we receive the event and continue execution here
      // Otherwise, it will be failed by timeout.
      session.awaitOutputEvent(TerminalSessionTerminatedEvent)
    }

    session.getInputChannel().send(TerminalWriteBytesEvent("exit".toByteArray() + ENTER_BYTES))
  }

  @Test
  fun `session emit TerminalSessionTerminatedEvent on after we send TerminalCloseEvent`() = timeoutRunBlocking(10.seconds) {
    val session = startTerminalSession(childScope("TerminalSession"))

    launch(start = CoroutineStart.UNDISPATCHED) {
      // The test will be finished once we receive the event and continue execution here
      // Otherwise, it will be failed by timeout.
      session.awaitOutputEvent(TerminalSessionTerminatedEvent)
    }

    session.getInputChannel().send(TerminalCloseEvent())
  }

  private suspend fun startTerminalSession(scope: CoroutineScope): TerminalSession {
    val session = TerminalSessionTestUtil.startTestTerminalSession(shellPath.toString(), projectRule.project, scope)
    session.awaitOutputEvent(TerminalPromptFinishedEvent)
    return session
  }
}