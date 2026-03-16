package com.intellij.terminal.tests.reworked.frontend

import com.intellij.terminal.frontend.toolwindow.impl.AgentWorkbenchTerminalPromotionAcquireResult
import com.intellij.terminal.frontend.toolwindow.impl.AgentWorkbenchTerminalPromotionActivationHandler
import com.intellij.terminal.frontend.toolwindow.impl.AgentWorkbenchTerminalPromotionController
import com.intellij.terminal.frontend.toolwindow.impl.AgentWorkbenchTerminalPromotionGate
import com.intellij.terminal.frontend.toolwindow.impl.AgentWorkbenchTerminalPromotionPresentationLease
import com.intellij.terminal.frontend.toolwindow.impl.AgentWorkbenchTerminalPromotionPresentationResult
import com.intellij.terminal.frontend.toolwindow.impl.AgentWorkbenchTerminalPromotionPresentationTracker
import com.intellij.terminal.frontend.toolwindow.impl.AgentWorkbenchTerminalProvider
import com.intellij.terminal.frontend.toolwindow.impl.createAgentWorkbenchPromotionBanner
import com.intellij.terminal.frontend.toolwindow.impl.matchAgentWorkbenchTerminalProvider
import com.intellij.terminal.frontend.toolwindow.impl.presentAgentWorkbenchTerminalPromotion
import com.intellij.testFramework.TestApplicationManager
import com.intellij.testFramework.runInEdtAndWait
import com.intellij.ui.EditorNotificationPanel
import com.intellij.ui.components.panels.ListLayout
import com.intellij.util.ui.UIUtil
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.plugins.terminal.TerminalBundle
import org.junit.Test
import javax.swing.JPanel

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
  fun `controller is available only when plugin is missing and not dismissed`() {
    val gate = TestPromotionGate()
    val controller = AgentWorkbenchTerminalPromotionController(
      gate = gate,
      presentationTracker = TestPromotionTracker(),
      shouldCheckGate = { true },
    )

    assertThat(controller.isPromotionAvailable()).isTrue()
    assertThat(gate.isDismissedCalls).isEqualTo(1)
  }

  @Test
  fun `controller is unavailable when promotion was already dismissed`() {
    val controller = AgentWorkbenchTerminalPromotionController(
      gate = TestPromotionGate(initiallyDismissed = true),
      presentationTracker = TestPromotionTracker(),
      shouldCheckGate = { true },
    )

    assertThat(controller.isPromotionAvailable()).isFalse()
  }

  @Test
  fun `controller is unavailable when registry disables promotion`() {
    val controller = AgentWorkbenchTerminalPromotionController(
      gate = TestPromotionGate(),
      presentationTracker = TestPromotionTracker(),
      shouldCheckGate = { false },
    )

    assertThat(controller.isPromotionAvailable()).isFalse()
  }

  @Test
  fun `controller reacquires promotion after active banner is released`() {
    val tracker = TestPromotionTracker()
    val controller = AgentWorkbenchTerminalPromotionController(
      gate = TestPromotionGate(),
      presentationTracker = tracker,
      shouldCheckGate = { true },
    )

    val firstAttempt = controller.tryAcquirePromotion()
    val secondAttempt = controller.tryAcquirePromotion()
    val firstLease = (firstAttempt as AgentWorkbenchTerminalPromotionAcquireResult.Acquired).lease
    firstLease.release()
    val thirdAttempt = controller.tryAcquirePromotion()

    assertThat(firstAttempt).isInstanceOf(AgentWorkbenchTerminalPromotionAcquireResult.Acquired::class.java)
    assertThat(secondAttempt).isSameAs(AgentWorkbenchTerminalPromotionAcquireResult.AlreadyPresented)
    assertThat(thirdAttempt).isInstanceOf(AgentWorkbenchTerminalPromotionAcquireResult.Acquired::class.java)
    assertThat(tracker.tryAcquireCalls).isEqualTo(3)
  }

  @Test
  fun `controller permanently blocks promotion after dismissal`() {
    val gate = TestPromotionGate()
    val controller = AgentWorkbenchTerminalPromotionController(
      gate = gate,
      presentationTracker = TestPromotionTracker(),
      shouldCheckGate = { true },
    )

    controller.dismissPromotion()

    assertThat(controller.isPromotionAvailable()).isFalse()
    assertThat(controller.tryAcquirePromotion()).isSameAs(AgentWorkbenchTerminalPromotionAcquireResult.Unavailable)
    assertThat(gate.markDismissedCalls).isEqualTo(1)
  }

  @Test
  fun `controller does not touch gate when plugin is installed`() {
    val gate = TestPromotionGate()
    val tracker = TestPromotionTracker()
    val controller = AgentWorkbenchTerminalPromotionController(
      gate = gate,
      presentationTracker = tracker,
      shouldCheckGate = { false },
    )

    val acquired = controller.tryAcquirePromotion()

    assertThat(controller.isPromotionAvailable()).isFalse()
    assertThat(acquired).isSameAs(AgentWorkbenchTerminalPromotionAcquireResult.Unavailable)
    assertThat(gate.isDismissedCalls).isZero()
    assertThat(tracker.tryAcquireCalls).isZero()
  }

  @Test
  fun `controller does not touch gate when registry disables promotion`() {
    val gate = TestPromotionGate()
    val tracker = TestPromotionTracker()
    val controller = AgentWorkbenchTerminalPromotionController(
      gate = gate,
      presentationTracker = tracker,
      shouldCheckGate = { false },
    )

    val acquired = controller.tryAcquirePromotion()

    assertThat(controller.isPromotionAvailable()).isFalse()
    assertThat(acquired).isSameAs(AgentWorkbenchTerminalPromotionAcquireResult.Unavailable)
    assertThat(gate.isDismissedCalls).isZero()
    assertThat(tracker.tryAcquireCalls).isZero()
  }

  @Test
  fun `presentation helper aborts when view is disposed before attach`() {
    var attachCalls = 0
    var shownCalls = 0
    var abortedCalls = 0

    val result = presentAgentWorkbenchTerminalPromotion(
      isProjectDisposed = { false },
      isViewDisposed = { true },
      attachBanner = {
        attachCalls += 1
        true
      },
      onShown = { shownCalls += 1 },
      onAborted = { abortedCalls += 1 },
    )

    assertThat(result).isEqualTo(AgentWorkbenchTerminalPromotionPresentationResult.ABORTED)
    assertThat(attachCalls).isZero()
    assertThat(shownCalls).isZero()
    assertThat(abortedCalls).isEqualTo(1)
  }

  @Test
  fun `presentation helper reports shown after banner attachment`() {
    var attachCalls = 0
    var shownCalls = 0
    var abortedCalls = 0

    val result = presentAgentWorkbenchTerminalPromotion(
      isProjectDisposed = { false },
      isViewDisposed = { false },
      attachBanner = {
        attachCalls += 1
        true
      },
      onShown = { shownCalls += 1 },
      onAborted = { abortedCalls += 1 },
    )

    assertThat(result).isEqualTo(AgentWorkbenchTerminalPromotionPresentationResult.SHOWN)
    assertThat(attachCalls).isEqualTo(1)
    assertThat(shownCalls).isEqualTo(1)
    assertThat(abortedCalls).isZero()
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
  fun `promotion banner height stays stable after returning to wide width`() {
    runInEdtAndWait {
      TestApplicationManager.getInstance()
      val bannerComponent = createAgentWorkbenchPromotionBanner(
        onInstallClicked = {},
        onClose = {},
      )
      val banner = checkNotNull(UIUtil.findComponentOfType(bannerComponent, EditorNotificationPanel::class.java)) {
        "Agent Workbench promotion banner is missing"
      }
      assertThat(banner.text).isEqualTo(TerminalBundle.message("agent.workbench.promotion.banner.text"))
      assertThat(banner.findLabelByName("Install")).isNotNull()

      val host = JPanel(ListLayout.vertical(0)).apply {
        add(bannerComponent)
      }

      layoutTopComponent(host, width = 320)
      val wideHeight = layoutTopComponent(host, width = 720)

      host.revalidate()
      bannerComponent.revalidate()

      val wideHeightAfterRelayout = layoutTopComponent(host, width = 720)
      val wideHeightAfterExtraRelayout = layoutTopComponent(host, width = 720)

      assertThat(wideHeight).isPositive()
      assertThat(wideHeightAfterRelayout).isEqualTo(wideHeight)
      assertThat(wideHeightAfterExtraRelayout).isEqualTo(wideHeight)
    }
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

  private fun layoutTopComponent(host: JPanel, width: Int): Int {
    host.setSize(width, 10_000)
    host.doLayout()
    host.validate()
    val component = host.components.single()
    component.doLayout()
    return component.height
  }

  private class TestPromotionGate(initiallyDismissed: Boolean = false) : AgentWorkbenchTerminalPromotionGate {
    var dismissed = initiallyDismissed
    var isDismissedCalls = 0
    var markDismissedCalls = 0

    override fun isDismissed(): Boolean {
      isDismissedCalls += 1
      return dismissed
    }

    override fun markDismissed() {
      markDismissedCalls += 1
      dismissed = true
    }
  }

  private class TestPromotionTracker : AgentWorkbenchTerminalPromotionPresentationTracker {
    private var activeLease = false
    var tryAcquireCalls = 0

    override fun tryAcquirePresentation(): AgentWorkbenchTerminalPromotionPresentationLease? {
      tryAcquireCalls += 1
      if (activeLease) {
        return null
      }

      activeLease = true
      var released = false
      return AgentWorkbenchTerminalPromotionPresentationLease {
        if (!released) {
          released = true
          activeLease = false
        }
      }
    }
  }
}
