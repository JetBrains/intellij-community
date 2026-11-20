package com.intellij.mcpserver.impl

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.util.CheckedDisposable
import com.intellij.terminal.frontend.view.TerminalShellLifecycle
import com.intellij.terminal.frontend.view.TerminalView
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.ui.EditorNotificationPanel
import com.intellij.util.ui.UIUtil
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.job
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.withContext
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import javax.swing.JComponent

@TestApplication
internal class McpServerTerminalPromotionViewTest {
  @AfterEach
  fun resetState() {
    McpServerTerminalPromotionDismissalState.reset()
  }

  @Test
  fun `banner uses the shared view and follows its lifetime`(): Unit = withView { host ->
    withContext(Dispatchers.EDT) {
      assertThat(showMcpServerTerminalPromotionBanner(
        projectFixture.get(), promotion, host.view, onBannerPrepared = {}, onBannerDisposed = {},
      )).isTrue()
    }
    val banner = host.banners.receive()
    withContext(Dispatchers.EDT) {
      assertThat(UIUtil.findComponentOfType(banner.first, EditorNotificationPanel::class.java)).isNotNull()
      assertThat(banner.second.isDisposed).isFalse()
    }
    host.scope.coroutineContext.job.cancel()
    host.scope.coroutineContext.job.join()
    assertThat(banner.second.isDisposed).isTrue()
  }

  @Test
  fun `shell commands show and remove banners without document integration`(): Unit = withView { host ->
    withContext(Dispatchers.EDT) {
      McpServerTerminalPromotionNotifier(projectFixture.get()).listenForAgentCommands(host.view) { promotion }
      host.listener.commandStarted("echo hello")
      host.listener.commandFinished("echo hello", 0)
      assertThat(host.banners.tryReceive().isFailure).isTrue()
      host.listener.commandStarted("claude")
    }
    val first = host.banners.receive()
    withContext(Dispatchers.EDT) {
      host.listener.commandFinished("claude", 0)
      assertThat(first.second.isDisposed).isTrue()
      host.listener.commandStarted("claude")
    }
    val second = host.banners.receive()
    withContext(Dispatchers.EDT) {
      assertThat(second.second).isNotSameAs(first.second)
      assertThat(second.second.isDisposed).isFalse()
      host.listener.commandFinished("claude", 1)
      assertThat(second.second.isDisposed).isTrue()
    }
  }

  @Test
  fun `command completion before resolution prevents a stale banner`(): Unit = withView { host ->
    val resolving = CompletableDeferred<Unit>()
    val result = CompletableDeferred<McpServerTerminalPromotion>()
    withContext(Dispatchers.EDT) {
      McpServerTerminalPromotionNotifier(projectFixture.get()).listenForAgentCommands(host.view) {
        resolving.complete(Unit)
        result.await()
      }
      host.listener.commandStarted("claude")
    }
    resolving.await()
    withContext(Dispatchers.EDT) {
      host.listener.commandFinished("claude", 0)
      host.listener.commandStarted("echo hello")
    }
    result.complete(promotion)
    host.scope.coroutineContext.job.children.toList().joinAll()
    assertThat(host.banners.tryReceive().isFailure).isTrue()
    withContext(Dispatchers.EDT) {
      host.listener.commandFinished("echo hello", 0)
      host.listener.commandStarted("claude")
    }
    assertThat(host.banners.receive().second.isDisposed).isFalse()
  }

  @Test
  fun `view closure cancels pending resolution and disposes the listener`(): Unit = withView { host ->
    val resolving = CompletableDeferred<Unit>()
    val result = CompletableDeferred<McpServerTerminalPromotion>()
    withContext(Dispatchers.EDT) {
      McpServerTerminalPromotionNotifier(projectFixture.get()).listenForAgentCommands(host.view) {
        resolving.complete(Unit)
        result.await()
      }
      host.listener.commandStarted("claude")
    }
    resolving.await()
    host.scope.cancel()
    host.scope.coroutineContext.job.join()
    assertThat(host.listenerOwner.isDisposed).isTrue()
    assertThat(host.banners.tryReceive().isFailure).isTrue()
  }

  private fun withView(action: suspend (Host) -> Unit): Unit = timeoutRunBlocking {
    val scope = CoroutineScope(coroutineContext + Job(coroutineContext.job))
    try {
      action(Host(scope))
    }
    finally {
      scope.cancel()
      scope.coroutineContext.job.join()
    }
  }

  private class Host(val scope: CoroutineScope) {
    val banners = Channel<Pair<JComponent, CheckedDisposable>>(Channel.UNLIMITED)
    lateinit var listener: TerminalShellLifecycle.Listener
    lateinit var listenerOwner: CheckedDisposable
    private val lifecycle = mock<TerminalShellLifecycle>()
    val view = mock<TerminalView>()

    init {
      doReturn(scope).whenever(view).coroutineScope
      doReturn(lifecycle).whenever(view).shellLifecycle
      doReturn(false).whenever(view).supportsOutputModel
      doAnswer { error("The banner must not read the output document") }.whenever(view).outputModels
      doAnswer { error("The banner must not await document shell integration") }.whenever(view).shellIntegrationDeferred
      doAnswer {
        listenerOwner = it.getArgument(0)
        listener = it.getArgument(1)
        null
      }.whenever(lifecycle).addListener(any<Disposable>(), any())
      doAnswer {
        check(banners.trySend(it.getArgument<JComponent>(0) to it.getArgument<CheckedDisposable>(1)).isSuccess)
        null
      }.whenever(view).setTopComponent(any(), any())
    }
  }

  companion object {
    private val projectFixture = projectFixture()
    private val promotion = McpServerTerminalPromotion(
      McpServerTerminalProvider.CLAUDE, McpServerTerminalPromotionIssue.ENABLE_SERVER, emptyList(), emptyList(),
    )
  }
}
