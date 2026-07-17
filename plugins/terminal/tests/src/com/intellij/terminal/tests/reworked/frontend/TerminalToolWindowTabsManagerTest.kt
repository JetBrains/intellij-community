// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.tests.reworked.frontend

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.RegisterToolWindowTask
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.terminal.frontend.toolwindow.TerminalTabsManagerListener
import com.intellij.terminal.frontend.toolwindow.TerminalToolWindowTab
import com.intellij.terminal.frontend.toolwindow.TerminalToolWindowTabsManager
import com.intellij.terminal.frontend.toolwindow.getTerminalTab
import com.intellij.terminal.frontend.view.TerminalView
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.disposableFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.ui.content.ContentFactory
import com.intellij.ui.content.ContentManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.plugins.terminal.TerminalToolWindowFactory
import org.junit.jupiter.api.Test

/**
 * Tests the tool-window tab management behavior of [TerminalToolWindowTabsManager]:
 * tab creation, adding to the content manager, attach/detach, listeners and closing.
 *
 * The terminal session is never started here: with the
 * [com.intellij.terminal.frontend.toolwindow.TerminalToolWindowTabBuilder.deferSessionStartUntilUiShown]
 * option the shell process is started only after the terminal component becomes showing, which never
 * happens in a headless test. So no real PTY process is spawned and mocking of the session manager
 * is not required for these scenarios.
 */
@TestApplication
@Suppress("DEPRECATION")  // the deprecated Disposer.isDisposed is the simplest fit for these assertions
internal class TerminalToolWindowTabsManagerTest {
  private val project: Project by projectFixture()
  private val disposable: Disposable by disposableFixture()

  @Test
  fun `createTab adds the tab to the tool window and notifies listeners`() = timeoutRunBlocking(context = Dispatchers.EDT) {
    withTerminalToolWindow { toolWindow, manager, listener ->
      val tab = manager.createTestTab(name = "Tab 1")

      assertThat(manager.tabs).containsExactly(tab)
      assertThat(toolWindow.contentManager.contents.toList()).containsExactly(tab.content)
      assertThat(tab.content.getTerminalTab()).isSameAs(tab)
      assertThat(tab.content.manager).isSameAs(toolWindow.contentManager)
      assertThat(tab.view.title.defaultTitle).isEqualTo("Tab 1")

      // terminalViewCreated is fired once (before the content is added), then tabAdded once.
      assertThat(listener.viewsCreated).containsExactly(tab.view)
      assertThat(listener.tabsAdded).containsExactly(tab)
      assertThat(listener.tabsDetached).isEmpty()
    }
  }

  @Test
  fun `createTab with shouldAddToToolWindow=false creates a detached tab`() = timeoutRunBlocking(context = Dispatchers.EDT) {
    withTerminalToolWindow { toolWindow, manager, listener ->
      val tab = manager.createTestTab(name = "Detached", addToToolWindow = false)
      // This tab is never added to a content manager, so nothing owns its content. Unlike the other tests,
      // project disposal won't reach it (it stays a Disposer-tree root and would leak the project via its
      // close listener), so it must be disposed explicitly.
      try {
        assertThat(manager.tabs).isEmpty()
        assertThat(toolWindow.contentManager.contentCount).isEqualTo(0)
        assertThat(tab.content.manager).isNull()

        // The view is always created, but the tab is not added anywhere.
        assertThat(listener.viewsCreated).containsExactly(tab.view)
        assertThat(listener.tabsAdded).isEmpty()
      }
      finally {
        Disposer.dispose(tab.content)
      }
    }
  }

  @Test
  fun `attachTab adds a detached tab to the tool window`() = timeoutRunBlocking(context = Dispatchers.EDT) {
    withTerminalToolWindow { toolWindow, manager, listener ->
      val tab = manager.createTestTab(name = "To attach", addToToolWindow = false)
      listener.clear()

      manager.attachTab(tab)

      assertThat(manager.tabs).containsExactly(tab)
      assertThat(toolWindow.contentManager.contents.toList()).containsExactly(tab.content)
      assertThat(tab.content.manager).isSameAs(toolWindow.contentManager)
      assertThat(listener.tabsAdded).containsExactly(tab)
    }
  }

  @Test
  fun `attachTab adds the tab to the explicitly provided content manager`() = timeoutRunBlocking(context = Dispatchers.EDT) {
    withTerminalToolWindow { toolWindow, manager, listener ->
      val otherContentManager: ContentManager = ContentFactory.getInstance().createContentManager(false, project)
      val tab = manager.createTestTab(name = "In split", addToToolWindow = false)
      listener.clear()

      manager.attachTab(tab, otherContentManager)

      assertThat(otherContentManager.contents.toList()).containsExactly(tab.content)
      assertThat(tab.content.manager).isSameAs(otherContentManager)
      // The tab is not in the main tool window content manager, so it is not reported by `tabs`.
      assertThat(toolWindow.contentManager.contentCount).isEqualTo(0)
      assertThat(listener.tabsAdded).containsExactly(tab)
    }
  }

  @Test
  fun `detachTab removes the content without disposing it and notifies listeners`() = timeoutRunBlocking(context = Dispatchers.EDT) {
    withTerminalToolWindow { toolWindow, manager, listener ->
      val tab = manager.createTestTab(name = "To detach")
      listener.clear()

      manager.detachTab(tab)

      assertThat(manager.tabs).isEmpty()
      assertThat(toolWindow.contentManager.contentCount).isEqualTo(0)
      assertThat(toolWindow.contentManager.contents.toList()).doesNotContain(tab.content)
      // The content is detached but not disposed, so the view and its process stay alive.
      assertThat(Disposer.isDisposed(tab.content)).isFalse()
      assertThat(tab.view.coroutineScope.isActive).isTrue()

      assertThat(listener.tabsDetached).containsExactly(tab)
      assertThat(listener.tabsAdded).isEmpty()
    }
  }

  @Test
  fun `detached tab can be attached back to the tool window`() = timeoutRunBlocking(context = Dispatchers.EDT) {
    withTerminalToolWindow { toolWindow, manager, listener ->
      val tab = manager.createTestTab(name = "Round trip")
      manager.detachTab(tab)
      listener.clear()

      manager.attachTab(tab)

      assertThat(manager.tabs).containsExactly(tab)
      assertThat(toolWindow.contentManager.contents.toList()).containsExactly(tab.content)
      assertThat(Disposer.isDisposed(tab.content)).isFalse()
      assertThat(listener.tabsAdded).containsExactly(tab)
    }
  }

  @Test
  fun `closeTab removes and disposes the content and cancels the view scope`() = timeoutRunBlocking(context = Dispatchers.EDT) {
    withTerminalToolWindow { toolWindow, manager, _ ->
      val tab = manager.createTestTab(name = "To close")

      manager.closeTab(tab)

      assertThat(manager.tabs).isEmpty()
      assertThat(toolWindow.contentManager.contentCount).isEqualTo(0)
      assertThat(Disposer.isDisposed(tab.content)).isTrue()
      // Disposing the content cancels the tab scope, which cancels the terminal view scope.
      assertThat(tab.view.coroutineScope.isActive).isFalse()
    }
  }

  @Test
  fun `tabs returns all opened tabs`() = timeoutRunBlocking(context = Dispatchers.EDT) {
    withTerminalToolWindow { toolWindow, manager, _ ->
      val tab1 = manager.createTestTab(name = "One")
      val tab2 = manager.createTestTab(name = "Two")
      val tab3 = manager.createTestTab(name = "Three")

      assertThat(manager.tabs).containsExactlyInAnyOrder(tab1, tab2, tab3)
      assertThat(toolWindow.contentManager.contentCount).isEqualTo(3)
    }
  }

  private fun TerminalToolWindowTabsManager.createTestTab(
    name: String? = null,
    addToToolWindow: Boolean = true,
    contentManager: ContentManager? = null,
  ): TerminalToolWindowTab {
    return createTabBuilder()
      .tabName(name)
      .requestFocus(false)  // Avoid tool window activation in the headless environment
      .deferSessionStartUntilUiShown(true)  // Do not start a real terminal process
      .contentManager(contentManager)
      .shouldAddToToolWindow(addToToolWindow)
      .createTab()
  }

  /**
   * Registers a fresh Terminal tool window, subscribes a [RecordingListener] and runs [body].
   *
   * No explicit cleanup is needed: [projectFixture] creates a fresh project for each test and disposes it
   * afterwards, which tears down the tool window (with all its contents) together with the project-level
   * [TerminalToolWindowTabsManager] service and its coroutine scope.
   */
  private fun withTerminalToolWindow(
    body: (toolWindow: ToolWindow, manager: TerminalToolWindowTabsManager, listener: RecordingListener) -> Unit,
  ) {
    val toolWindow = ToolWindowManager.getInstance(project)
      .registerToolWindow(RegisterToolWindowTask(id = TerminalToolWindowFactory.TOOL_WINDOW_ID))
    val manager = TerminalToolWindowTabsManager.getInstance(project)
    val listener = RecordingListener()
    project.messageBus.connect(disposable).subscribe(TerminalTabsManagerListener.TOPIC, listener)
    body(toolWindow, manager, listener)
  }

  private class RecordingListener : TerminalTabsManagerListener {
    val viewsCreated: MutableList<TerminalView> = mutableListOf()
    val tabsAdded: MutableList<TerminalToolWindowTab> = mutableListOf()
    val tabsDetached: MutableList<TerminalToolWindowTab> = mutableListOf()

    override fun terminalViewCreated(view: TerminalView) {
      viewsCreated.add(view)
    }

    override fun tabAdded(tab: TerminalToolWindowTab) {
      tabsAdded.add(tab)
    }

    override fun tabDetached(tab: TerminalToolWindowTab) {
      tabsDetached.add(tab)
    }

    fun clear() {
      viewsCreated.clear()
      tabsAdded.clear()
      tabsDetached.clear()
    }
  }
}
