package com.intellij.terminal.tests.reworked.frontend

import com.intellij.terminal.frontend.toolwindow.impl.AgentWorkbenchTerminalPromotionActivationHandler
import com.intellij.terminal.frontend.toolwindow.impl.AgentWorkbenchTerminalPromotionController
import com.intellij.terminal.frontend.toolwindow.impl.AgentWorkbenchTerminalPromotionGate
import com.intellij.terminal.frontend.toolwindow.impl.AgentWorkbenchTerminalProvider
import com.intellij.terminal.frontend.toolwindow.impl.matchAgentWorkbenchTerminalProvider
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

internal class AgentWorkbenchTerminalPromotionNotifierTest {
  @Test
  fun `matches codex command`() {
    assertThat(matchAgentWorkbenchTerminalProvider("codex --help")).isEqualTo(AgentWorkbenchTerminalProvider.CODEX)
  }

  @Test
  fun `matches claude path command`() {
    assertThat(matchAgentWorkbenchTerminalProvider("/usr/local/bin/claude --resume"))
      .isEqualTo(AgentWorkbenchTerminalProvider.CLAUDE)
  }

  @Test
  fun `matches windows executable suffix`() {
    assertThat(matchAgentWorkbenchTerminalProvider("C:\\Tools\\Codex.exe resume thread-1"))
      .isEqualTo(AgentWorkbenchTerminalProvider.CODEX)
  }

  @Test
  fun `skips leading environment assignments`() {
    assertThat(matchAgentWorkbenchTerminalProvider("FOO=1 BAR=2 claude --print"))
      .isEqualTo(AgentWorkbenchTerminalProvider.CLAUDE)
  }

  @Test
  fun `ignores unrelated command`() {
    assertThat(matchAgentWorkbenchTerminalProvider("git status")).isNull()
  }

  @Test
  fun `controller is available only when plugin is missing and not shown`() {
    val controller = AgentWorkbenchTerminalPromotionController(
      gate = object : AgentWorkbenchTerminalPromotionGate {
        override fun isShown(): Boolean = false
        override fun tryMarkShown(): Boolean = true
      },
      shouldCheckGate = { true },
    )

    assertThat(controller.isPromotionAvailable()).isTrue()
  }

  @Test
  fun `controller is unavailable when promotion was already shown`() {
    val controller = AgentWorkbenchTerminalPromotionController(
      gate = object : AgentWorkbenchTerminalPromotionGate {
        override fun isShown(): Boolean = true
        override fun tryMarkShown(): Boolean = true
      },
      shouldCheckGate = { true },
    )

    assertThat(controller.isPromotionAvailable()).isFalse()
  }

  @Test
  fun `controller is unavailable when registry disables promotion`() {
    val controller = AgentWorkbenchTerminalPromotionController(
      gate = object : AgentWorkbenchTerminalPromotionGate {
        override fun isShown(): Boolean = false
        override fun tryMarkShown(): Boolean = true
      },
      shouldCheckGate = { false },
    )

    assertThat(controller.isPromotionAvailable()).isFalse()
  }

  @Test
  fun `controller acquires promotion only once when plugin is missing`() {
    var attempts = 0
    val controller = AgentWorkbenchTerminalPromotionController(
      gate = object : AgentWorkbenchTerminalPromotionGate {
        override fun isShown(): Boolean = false
        override fun tryMarkShown(): Boolean = ++attempts == 1
      },
      shouldCheckGate = { true },
    )

    val firstAttempt = controller.tryAcquirePromotion()
    val secondAttempt = controller.tryAcquirePromotion()

    assertThat(firstAttempt).isTrue()
    assertThat(secondAttempt).isFalse()
  }

  @Test
  fun `controller does not touch gate when plugin is installed`() {
    var gateCalls = 0
    val controller = AgentWorkbenchTerminalPromotionController(
      gate = object : AgentWorkbenchTerminalPromotionGate {
        override fun isShown(): Boolean {
          gateCalls += 1
          return false
        }

        override fun tryMarkShown(): Boolean {
          gateCalls += 1
          return true
        }
      },
      shouldCheckGate = { false },
    )

    val acquired = controller.tryAcquirePromotion()

    assertThat(controller.isPromotionAvailable()).isFalse()
    assertThat(acquired).isFalse()
    assertThat(gateCalls).isZero()
  }

  @Test
  fun `controller does not touch gate when registry disables promotion`() {
    var gateCalls = 0
    val controller = AgentWorkbenchTerminalPromotionController(
      gate = object : AgentWorkbenchTerminalPromotionGate {
        override fun isShown(): Boolean {
          gateCalls += 1
          return false
        }

        override fun tryMarkShown(): Boolean {
          gateCalls += 1
          return true
        }
      },
      shouldCheckGate = { false },
    )

    val acquired = controller.tryAcquirePromotion()

    assertThat(controller.isPromotionAvailable()).isFalse()
    assertThat(acquired).isFalse()
    assertThat(gateCalls).isZero()
  }

  @Test
  fun `activation handler skips disposed project`() {
    var activationAttempts = 0
    var successCallbacks = 0

    AgentWorkbenchTerminalPromotionActivationHandler(
      isProjectDisposed = { true },
      activateToolWindow = {
        activationAttempts += 1
        true
      },
      onActivationSucceeded = { successCallbacks += 1 },
    ).activateAndLogSuccess()

    assertThat(activationAttempts).isZero()
    assertThat(successCallbacks).isZero()
  }

  @Test
  fun `activation handler logs success after activation`() {
    var successCallbacks = 0

    AgentWorkbenchTerminalPromotionActivationHandler(
      isProjectDisposed = { false },
      activateToolWindow = { true },
      onActivationSucceeded = { successCallbacks += 1 },
    ).activateAndLogSuccess()

    assertThat(successCallbacks).isEqualTo(1)
  }

  @Test
  fun `activation handler skips success logging when tool window is unavailable`() {
    var successCallbacks = 0

    AgentWorkbenchTerminalPromotionActivationHandler(
      isProjectDisposed = { false },
      activateToolWindow = { false },
      onActivationSucceeded = { successCallbacks += 1 },
    ).activateAndLogSuccess()

    assertThat(successCallbacks).isZero()
  }

  @Test
  fun `frontend descriptor registers agent workbench listener`() {
    val descriptor = checkNotNull(javaClass.classLoader.getResource("intellij.terminal.frontend.xml")) {
      "Frontend terminal descriptor is missing"
    }.readText()

    assertThat(descriptor)
      .contains("AgentWorkbenchTerminalPromotionNotifier")
      .contains("com.intellij.terminal.frontend.toolwindow.TerminalTabsManagerListener")
  }
}
