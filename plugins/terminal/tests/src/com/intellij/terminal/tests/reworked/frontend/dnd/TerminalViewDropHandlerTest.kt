// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.tests.reworked.frontend.dnd

import com.intellij.ide.dnd.DnDEvent
import com.intellij.openapi.application.EDT
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.eel.EelDescriptor
import com.intellij.platform.eel.provider.getEelDescriptor
import com.intellij.platform.util.coroutines.childScope
import com.intellij.terminal.frontend.view.TerminalView
import com.intellij.terminal.frontend.view.impl.TerminalOutputScrollingModel
import com.intellij.terminal.frontend.view.impl.dnd.TerminalViewDropHandler
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.job
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.plugins.terminal.session.ShellName
import org.jetbrains.plugins.terminal.session.TerminalStartupOptions
import org.jetbrains.plugins.terminal.session.impl.TerminalSession
import org.jetbrains.plugins.terminal.session.impl.TerminalStartupOptionsImpl
import org.jetbrains.plugins.terminal.startup.TerminalProcessType
import org.jetbrains.plugins.terminal.view.impl.TerminalSendTextBuilderImpl
import org.jetbrains.plugins.terminal.view.impl.TerminalSendTextOptions
import org.junit.jupiter.api.Test
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import java.nio.file.Path
import javax.swing.JPanel
import kotlin.io.path.createFile

private val TEST_SHELL = ShellName.BASH

/**
 * Tests how [TerminalViewDropHandler] sends resolved dropped content to the terminal and handles
 * post-send behavior and no-op drops.
 */
@TestApplication
internal class TerminalViewDropHandlerTest {
  private val projectFixture = projectFixture()
  private val tempDirFixture = tempPathFixture()

  private val project: Project get() = projectFixture.get()
  private val tempDir: Path get() = tempDirFixture.get()

  // ---------- successful drops ----------

  @Test
  fun `a drop uses bracketed paste mode and does not execute the text`() {
    timeoutRunBlocking(context = Dispatchers.EDT) {
      val file = tempDir.resolve("script.sh").createFile()

      val sent = drop(ideDrag(file))

      assertThat(sent.single().useBracketedPasteMode).isTrue()
      assertThat(sent.single().shouldExecute).isFalse()
    }
  }

  /** The handler restores the view of the terminal after a send, so the inserted text stays visible. */
  @Test
  fun `a drop scrolls to the cursor`() {
    timeoutRunBlocking(context = Dispatchers.EDT) {
      val file = tempDir.resolve("script.sh").createFile()
      val scrollingModel = mock<TerminalOutputScrollingModel>()

      drop(ideDrag(file), scrollingModel = scrollingModel)

      verify(scrollingModel).scrollToCursor(true)
    }
  }

  @Test
  fun `a drop that sends nothing does not scroll to the cursor`() {
    timeoutRunBlocking(context = Dispatchers.EDT) {
      val scrollingModel = mock<TerminalOutputScrollingModel>()

      val sent = drop(
        nativeTextDrag("   \n  "),
        scrollingModel = scrollingModel,
      )

      assertThat(sent).isEmpty()
      verifyNoInteractions(scrollingModel)
    }
  }

  // ---------- the drops that send nothing ----------

  @Test
  fun `a drop of blank text sends nothing`() {
    timeoutRunBlocking(context = Dispatchers.EDT) {
      assertThat(drop(nativeTextDrag("   \n  "))).isEmpty()
    }
  }

  @Test
  fun `a drop with no payload at all sends nothing`() {
    timeoutRunBlocking(context = Dispatchers.EDT) {
      assertThat(drop(emptyDrag())).isEmpty()
    }
  }

  @Test
  fun `a drop of files that have no nio path sends nothing`() {
    timeoutRunBlocking(context = Dispatchers.EDT) {
      // A VirtualFile without a nio path is what a ThinClientNodeVirtualFile looks like in the monolith.
      val file = mock<VirtualFile> { on { path } doReturn "/some/remote/path" }

      assertThat(drop(ideDragOf(listOf(file)))).isEmpty()
    }
  }

  /**
   * The terminal context needs a started session, because the session carries the environment. Before the
   * session starts, a drop has to do nothing rather than guess the environment.
   */
  @Test
  fun `a drop before the session starts sends nothing`() {
    timeoutRunBlocking(context = Dispatchers.EDT) {
      val file = tempDir.resolve("script.sh").createFile()

      assertThat(drop(ideDrag(file), sessionStarted = false)).isEmpty()
    }
  }

  @Test
  fun `a drop on a view whose scope is already cancelled sends nothing`() {
    timeoutRunBlocking(context = Dispatchers.EDT) {
      val file = tempDir.resolve("script.sh").createFile()
      val sent = mutableListOf<TerminalSendTextOptions>()
      val viewScope = childScope("cancelled view scope")
      viewScope.cancel()

      TerminalViewDropHandler(
        project = project,
        terminalView = recordingView(viewScope, sent, sessionStarted = true),
        scrollingModel = mock(),
      ).drop(ideDrag(file))

      assertThat(sent).isEmpty()
    }
  }

  // ---------- helpers ----------

  /**
   * Runs the handler and returns everything that reached the terminal.
   *
   * The handler launches on the scope of the view, so the helper joins the children of that scope instead
   * of sleeping.
   */
  private suspend fun CoroutineScope.drop(
    event: DnDEvent,
    sessionStarted: Boolean = true,
    scrollingModel: TerminalOutputScrollingModel = mock(),
  ): List<TerminalSendTextOptions> {
    val sent = mutableListOf<TerminalSendTextOptions>()
    val viewScope = childScope("view scope")

    try {
      TerminalViewDropHandler(
        project = project,
        terminalView = recordingView(viewScope, sent, sessionStarted),
        scrollingModel = scrollingModel,
      ).drop(event)

      viewScope.coroutineContext.job.children.forEach { it.join() }
      return sent
    }
    finally {
      viewScope.cancel()
    }
  }

  private fun projectEel(): EelDescriptor = project.getEelDescriptor()

  private fun ideDrag(vararg files: Path): DnDEvent = ideDragOf(files.map(::virtualFileOf))

  /**
   * A [TerminalView] test double that records every send.
   *
   * The handler needs five members: the scope for the launch, two deferred values for the terminal
   * context, the builder for the send, and the component for the focus request.
   */
  private fun recordingView(
    scope: CoroutineScope,
    sent: MutableList<TerminalSendTextOptions>,
    sessionStarted: Boolean,
  ): TerminalView {
    val session = mock<TerminalSession> { on { eelDescriptor } doReturn projectEel() }
    val startupOptions: TerminalStartupOptions = TerminalStartupOptionsImpl(
      shellCommand = listOf("/bin/${TEST_SHELL.value}"),
      workingDirectory = tempDir.toString(),
      envVariables = emptyMap(),
      processType = TerminalProcessType.SHELL,
      pid = null,
    )

    return mock<TerminalView> {
      on { coroutineScope } doReturn scope
      on { sessionDeferred } doReturn if (sessionStarted) CompletableDeferred(session) else CompletableDeferred()
      on { startupOptionsDeferred } doReturn CompletableDeferred(startupOptions)
      // getRunningProcessCommandLine() reads this for a shell process. An incomplete value means
      // "no command is running", which keeps the FUS payload out of these assertions.
      on { shellIntegrationDeferred } doReturn CompletableDeferred()
      on { preferredFocusableComponent } doReturn JPanel()
      on { createSendTextBuilder() } doReturn TerminalSendTextBuilderImpl { options ->
        sent += options
        true
      }
    }
  }
}
