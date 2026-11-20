// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.tests.reworked.frontend.ketraterm

import com.intellij.ide.SaveAndSyncHandler
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.UI
import com.intellij.openapi.util.Disposer
import com.intellij.platform.util.coroutines.childScope
import com.intellij.terminal.frontend.session.ReworkedLocalTerminalRunner
import com.intellij.terminal.frontend.session.ketraterm.createKetraTermTerminalSession
import com.intellij.terminal.frontend.toolwindow.impl.AgentWorkbenchTerminalProvider
import com.intellij.terminal.frontend.toolwindow.impl.showAgentWorkbenchPromotionBanner
import com.intellij.terminal.frontend.view.ketraterm.KetraTermTerminalView
import com.intellij.terminal.tests.reworked.util.LoopbackTtyConnector
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.junit5.RegistryKey
import com.intellij.testFramework.replaceService
import com.intellij.ui.EditorNotificationPanel
import com.intellij.util.ui.UIUtil
import com.intellij.util.asDisposable
import com.jediterm.core.util.TermSize
import io.github.ketraterm.shell.launch.TerminalShellLaunch
import io.github.ketraterm.workspace.TerminalProfile
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicInteger
import javax.swing.JLabel
import kotlinx.coroutines.cancel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.job
import kotlinx.coroutines.withContext
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.plugins.terminal.JBTerminalSystemSettingsProvider
import org.jetbrains.plugins.terminal.ShellStartupOptions
import org.jetbrains.plugins.terminal.TerminalEmulatorType
import org.jetbrains.plugins.terminal.TerminalOptionsProvider
import org.jetbrains.plugins.terminal.startup.TerminalProcessType
import org.jetbrains.plugins.terminal.view.shellIntegration.TerminalOutputStatus
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@TestApplication
internal class KetraTermPlatformServicesTest {
  @Test
  fun `host panels coexist and dispose independently`(): Unit = timeoutRunBlocking {
    val scope = childScope("KetraTerm host panels", Dispatchers.Default)
    try {
      withContext(Dispatchers.UI) {
        val view = KetraTermTerminalView(projectFixture.get(), JBTerminalSystemSettingsProvider(), scope)
        val first = JLabel("First")
        val second = JLabel("Second")
        val firstOwner = Disposer.newDisposable(scope.asDisposable(), "first panel")
        val secondOwner = Disposer.newDisposable(scope.asDisposable(), "second panel")
        view.setTopComponent(first, firstOwner)
        view.setTopComponent(second, secondOwner)
        val container = first.parent
        assertThat(second.parent).isSameAs(container)
        assertThat(container.components).containsExactly(first, second)
        view.component.setSize(800, 480)
        view.component.doLayout()
        container.doLayout()
        assertThat(first.height).isPositive()
        assertThat(second.y).isGreaterThanOrEqualTo(first.y + first.height)
        Disposer.dispose(firstOwner)
        assertThat(first.parent).isNull()
        assertThat(container.components).containsExactly(second)
        Disposer.dispose(secondOwner)
        assertThat(container.parent).isNull()
      }
    }
    finally {
      scope.cancel()
      scope.coroutineContext.job.join()
    }
  }

  @Test
  @RegistryKey(key = "terminal.use.EelApi", value = "true")
  fun `agent startup keeps its command without shell hooks`(@TempDir directory: Path) {
    val runner = ReworkedLocalTerminalRunner(projectFixture.get())
    val command = listOf("powershell.exe", "-Command", "Write-Output agent")
    try {
      val configured = runner.configureStartupOptions(ShellStartupOptions.Builder()
        .shellCommand(command)
        .workingDirectory(directory.toString())
        .emulatorType(TerminalEmulatorType.KetraTerm)
        .processType(TerminalProcessType.NON_SHELL)
        .build())
      assertThat(configured.shellCommand).isEqualTo(command)
      assertThat(configured.shellIntegration).isNull()
      assertThat(runner.shellLaunch).isNull()
    }
    finally {
      runner.shellLaunch?.close()
    }
  }

  @Test
  @RegistryKey(key = "terminal.use.EelApi", value = "true")
  fun `disabled shell integration keeps the shell command`(@TempDir directory: Path) {
    val settings = TerminalOptionsProvider.instance
    val previous = settings.shellIntegration
    val runner = ReworkedLocalTerminalRunner(projectFixture.get())
    try {
      settings.shellIntegration = false
      val command = listOf("bash", "--noprofile")
      val configured = runner.configureStartupOptions(ShellStartupOptions.Builder()
        .shellCommand(command)
        .workingDirectory(directory.toString())
        .emulatorType(TerminalEmulatorType.KetraTerm)
        .processType(TerminalProcessType.SHELL)
        .build())
      assertThat(configured.shellCommand).isEqualTo(command)
      assertThat(configured.shellIntegration).isNull()
      assertThat(runner.shellLaunch).isNull()
    }
    finally {
      runner.shellLaunch?.close()
      settings.shellIntegration = previous
    }
  }

  @Test
  fun `agent promotion uses the native view and releases its presentation`(): Unit = timeoutRunBlocking {
    val scope = childScope("KetraTerm agent promotion", Dispatchers.Default)
    val project = projectFixture.get()
    val released = AtomicInteger()
    try {
      withContext(Dispatchers.UI) {
        val view = KetraTermTerminalView(project, JBTerminalSystemSettingsProvider(), scope)
        assertThat(showAgentWorkbenchPromotionBanner(
          project, AgentWorkbenchTerminalProvider.CODEX, view, dismissPromotion = {}, onBannerDisposed = { released.incrementAndGet() },
        )).isTrue()
        assertThat(UIUtil.findComponentOfType(view.component, EditorNotificationPanel::class.java)).isNotNull()
        assertThat(released.get()).isZero()
      }
    }
    finally {
      scope.cancel()
      scope.coroutineContext.job.join()
    }
    assertThat(released.get()).isEqualTo(1)
  }

  @Test
  fun `native command completion schedules an IntelliJ refresh`(
    @TempDir scripts: Path,
    @TestDisposable disposable: Disposable,
  ): Unit = timeoutRunBlocking {
    val scope = childScope("KetraTerm command refresh", Dispatchers.Default)
    val project = projectFixture.get()
    val refresh = CompletableDeferred<Unit>()
    val handler = mock<SaveAndSyncHandler>()
    doAnswer { refresh.complete(Unit); null }.whenever(handler).scheduleRefresh()
    ApplicationManager.getApplication().replaceService(SaveAndSyncHandler::class.java, handler, disposable)
    val connector = LoopbackTtyConnector()
    val launch = TerminalShellLaunch.prepare(TerminalProfile("test", "test", listOf("pwsh")), scriptDirectory = scripts)
    val options = ShellStartupOptions.Builder().shellCommand(listOf("pwsh")).initialTermSize(TermSize(80, 24)).build()
    try {
      val session = createKetraTermTerminalSession(
        project, connector, options, JBTerminalSystemSettingsProvider(), scope, shellLaunch = launch,
      )
      val view = withContext(Dispatchers.UI) {
        KetraTermTerminalView(project, JBTerminalSystemSettingsProvider(), scope).also { it.connectToSession(session) }
      }
      connector.feed("\u001b]133;A\u0007PS> \u001b]133;B\u0007")
      val integration = view.shellLifecycle
      assertThat(view.supportsOutputModel).isFalse()
      integration.outputStatus.first { it == TerminalOutputStatus.TypingCommand }
      connector.feed("echo done\u001b]133;C\u0007\r\n")
      integration.outputStatus.first { it == TerminalOutputStatus.ExecutingCommand }
      assertThat(refresh.isCompleted).isFalse()
      connector.feed("done\r\n\u001b]133;D;0\u0007")
      refresh.await()
      integration.outputStatus.first { it == TerminalOutputStatus.WaitingForPrompt }
    }
    finally {
      connector.close()
      scope.cancel()
      scope.coroutineContext.job.join()
      launch.close()
    }
  }

  companion object {
    private val projectFixture = projectFixture()
  }
}
