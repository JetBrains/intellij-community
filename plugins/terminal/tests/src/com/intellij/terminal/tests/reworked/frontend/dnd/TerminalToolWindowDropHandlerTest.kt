// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.tests.reworked.frontend.dnd

import com.intellij.ide.dnd.DnDEvent
import com.intellij.ide.impl.HeadlessDataManager
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.DataSink
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.actionSystem.UiDataProvider
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.WriteIntentReadAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.RegisterToolWindowTask
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.ex.ToolWindowEx
import com.intellij.platform.util.coroutines.childScope
import com.intellij.terminal.frontend.toolwindow.getTerminalTab
import com.intellij.terminal.frontend.toolwindow.impl.dnd.TerminalToolWindowDropHandler
import com.intellij.terminal.frontend.toolwindow.impl.shouldUseReworkedTerminal
import com.intellij.terminal.tests.reworked.frontend.withTerminalToolWindowManager
import com.intellij.terminal.tests.reworked.util.TerminalTestUtil.setTerminalEngineForTest
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import com.intellij.ui.content.ContentFactory
import com.intellij.ui.content.ContentManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.job
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.plugins.terminal.TerminalEngine
import org.jetbrains.plugins.terminal.TerminalToolWindowFactory
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import java.awt.Component
import java.awt.Point
import java.nio.file.Path
import javax.swing.JPanel
import kotlin.io.path.createDirectory
import kotlin.io.path.createFile

/**
 * Tests on [TerminalToolWindowDropHandler].
 */
@TestApplication
internal class TerminalToolWindowDropHandlerTest {
  private val projectFixture = projectFixture()
  private val tempDirFixture = tempPathFixture()

  private val project: Project get() = projectFixture.get()
  private val tempDir: Path get() = tempDirFixture.get()

  @TestDisposable
  private lateinit var disposable: Disposable

  /**
   * [HeadlessDataManager] ignores the component hierarchy by default. Without the production data manager
   * every pick stays empty, and the assertions say nothing.
   */
  @BeforeEach
  fun useProductionDataManager() {
    HeadlessDataManager.fallbackToProductionDataManager(disposable)
  }

  // ----------- the tab that a drop creates ---------------

  @Test
  fun `a drop of a file creates a tab that starts in the parent directory of that file`() {
    timeoutRunBlocking(context = Dispatchers.EDT) {
      setTerminalEngineForTest(TerminalEngine.REWORKED, disposable)
      // A drop creates a reworked tab only while this holds. Otherwise it starts a real shell process
      // through the classic tool window manager, which this test must never do.
      assertThat(shouldUseReworkedTerminal()).isTrue()

      val toolWindow = registerTerminalToolWindow()
      // A content manager of its own, so the assertions see only the tab that the drop creates.
      val contentManager = ContentFactory.getInstance().createContentManager(false, project)
      val directory = tempDir.resolve("scripts").createDirectory()
      val droppedFile = directory.resolve("build.sh").createFile()

      withTerminalToolWindowManager(project) {
        this@timeoutRunBlocking.drop(droppedFile, toolWindow, contentManager)

        val tab = contentManager.contents.single().getTerminalTab()
        assertThat(tab).isNotNull()
        assertThat(tab!!.processOptions.workingDirectory).isEqualTo(directory.toString())
      }
    }
  }

  // ----------- which content manager picks for a drop ---------------
  // A split tool window holds one content manager for each part, so the handler has to pick the manager under
  // the drop point. A wrong pick puts the new terminal tab in the wrong part. A pick of nothing creates no
  // tab at all.

  /**
   * Both parts of the layout supply a content manager, and the layout stays the same in both drops. Only
   * the point moves, so the point alone decides the pick.
   */
  @Test
  fun `the drop point picks the content manager of the part under it`() {
    timeoutRunBlocking(context = Dispatchers.EDT) {
      val outerManager = mock<ContentManager>()
      val innerManager = mock<ContentManager>()
      val outer = partProviding(outerManager, size = 100)
      outer.add(partProviding(innerManager, size = 20, origin = 10))

      assertThat(contentManagerAt(outer, Point(15, 15))).isSameAs(innerManager)
      assertThat(contentManagerAt(outer, Point(90, 90))).isSameAs(outerManager)
    }
  }

  // ---------- the drops that pick nothing ----------

  @Test
  fun `a drop without a handler component picks nothing`() {
    timeoutRunBlocking(context = Dispatchers.EDT) {
      assertThat(contentManagerAt(handlerComponent = null, point = Point(50, 50))).isNull()
    }
  }

  @Test
  fun `a drop without a point picks nothing`() {
    timeoutRunBlocking(context = Dispatchers.EDT) {
      val part = partProviding(mock<ContentManager>(), size = 100)

      assertThat(contentManagerAt(part, point = null)).isNull()
    }
  }

  @Test
  fun `a drop beyond the handler component picks nothing`() {
    timeoutRunBlocking(context = Dispatchers.EDT) {
      val part = partProviding(mock<ContentManager>(), size = 100)

      assertThat(contentManagerAt(part, Point(500, 500))).isNull()
    }
  }

  // ---------- helpers ----------

  /**
   * Drops [file] on [toolWindow] at a point that reports [contentManager], and waits for the new tab.
   */
  private suspend fun CoroutineScope.drop(file: Path, toolWindow: ToolWindowEx, contentManager: ContentManager) {
    val handlerScope = childScope("drop handler scope")

    try {
      val handler = TerminalToolWindowDropHandler.createDropHandler(toolWindow, handlerScope)
      val event = dropEventOf(virtualFileOf(file), partProviding(contentManager, size = 100), Point(50, 50))

      WriteIntentReadAction.run { handler.drop(event) }

      handlerScope.coroutineContext.job.children.forEach { it.join() }
    }
    finally {
      handlerScope.cancel()
    }
  }

  private fun registerTerminalToolWindow(): ToolWindowEx =
    ToolWindowManager.getInstance(project)
      .registerToolWindow(RegisterToolWindowTask(id = TerminalToolWindowFactory.TOOL_WINDOW_ID)) as ToolWindowEx

  /** A drop of [file] that lands on [handlerComponent] at [point]. */
  private fun dropEventOf(file: VirtualFile, handlerComponent: Component, point: Point): DnDEvent = mock {
    on { attachedObject } doReturn ideDragPayload(listOf(file))
    on { this.handlerComponent } doReturn handlerComponent
    on { this.point } doReturn point
  }

  /** The content manager that a drop of [point] on [handlerComponent] picks, or `null` for no pick. */
  private fun contentManagerAt(handlerComponent: Component?, point: Point?): ContentManager? {
    val event = mock<DnDEvent> {
      on { this.handlerComponent } doReturn handlerComponent
      on { this.point } doReturn point
    }
    val dataContext = with(TerminalToolWindowDropHandler) { event.resolveDataContextAtDropPoint() }
    return dataContext?.getData(PlatformDataKeys.TOOL_WINDOW_CONTENT_MANAGER)
  }

  /**
   * A part of the tool window that reports [manager] as its content manager.
   *
   * The part needs explicit bounds, because a headless test never lays out a component.
   */
  private fun partProviding(manager: ContentManager, size: Int, origin: Int = 0): JPanel {
    val panel = object : JPanel(null), UiDataProvider {
      override fun uiDataSnapshot(sink: DataSink) {
        sink[PlatformDataKeys.TOOL_WINDOW_CONTENT_MANAGER] = manager
      }
    }
    panel.setBounds(origin, origin, size, size)
    return panel
  }
}
