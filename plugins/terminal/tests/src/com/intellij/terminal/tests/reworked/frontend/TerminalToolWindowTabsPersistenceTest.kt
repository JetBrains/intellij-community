// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.tests.reworked.frontend

import com.intellij.ide.trustedProjects.TrustedProjects
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.RegisterToolWindowTask
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.terminal.frontend.TerminalToolWindowEditorTabPersistenceProvider
import com.intellij.terminal.frontend.TerminalToolWindowEditorTabSupport
import com.intellij.terminal.frontend.action.TerminalRenameTabAction
import com.intellij.terminal.frontend.toolwindow.TerminalTabsManagerListener
import com.intellij.terminal.frontend.toolwindow.TerminalToolWindowTab
import com.intellij.terminal.frontend.toolwindow.getTerminalTab
import com.intellij.terminal.frontend.toolwindow.impl.TerminalInEditorSupport
import com.intellij.terminal.frontend.toolwindow.impl.getPendingTerminalTab
import com.intellij.terminal.frontend.view.TerminalView
import com.intellij.terminal.tests.reworked.util.TerminalTestUtil
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.disposableFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.plugins.terminal.TerminalEngine
import org.jetbrains.plugins.terminal.TerminalToolWindowFactory
import org.jetbrains.plugins.terminal.TerminalToolWindowInitializer
import org.jetbrains.plugins.terminal.settings.impl.TerminalSessionPersistedTab
import org.jetbrains.plugins.terminal.settings.impl.TerminalTabsStorage
import org.jetbrains.plugins.terminal.startup.TerminalProcessType
import org.junit.jupiter.api.Test
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Tests terminal tab persistence and restoring driven by the real tool window initializer
 * ([TerminalToolWindowInitializer.performInitialization]).
 *
 * The [TerminalTabsStorage] is used as-is: it is a plain in-memory holder of the tab state,
 * so the test seeds it via [TerminalTabsStorage.updateStoredTabs] and reads it back via
 * [TerminalTabsStorage.getStoredTabs] without spawning any real shell process.
 *
 * No explicit tool window cleanup is needed: [projectFixture] recreates and disposes the project for each test,
 * which tears down the registered tool window together with the project-level services. Only the application-level
 * terminal engine override is restored, via the test-scoped [disposable].
 */
@TestApplication
internal class TerminalToolWindowTabsPersistenceTest {
  private val project: Project by projectFixture()
  private val disposable: Disposable by disposableFixture()

  private val storedTab1 = TerminalSessionPersistedTab(
    name = "Restored 1",
    isUserDefinedName = true,
    shellCommand = listOf("/bin/zsh"),
    workingDirectory = "/tmp/one",
    envVariables = mapOf("FOO" to "bar"),
    processType = TerminalProcessType.SHELL,
  )

  private val storedTab2 = TerminalSessionPersistedTab(
    name = "Restored 2",
    isUserDefinedName = false,
    shellCommand = null,
    workingDirectory = "/tmp/two",
    envVariables = emptyMap(),
    processType = TerminalProcessType.NON_SHELL,
  )

  private val storedTab3 = TerminalSessionPersistedTab(
    name = "Restored 3",
    isUserDefinedName = false,
    shellCommand = listOf("/bin/bash"),
    workingDirectory = "/tmp/three",
    envVariables = emptyMap(),
    processType = TerminalProcessType.SHELL,
  )

  @Test
  fun `only the first stored tab is built on tool window initialization`(): Unit = runBlocking(Dispatchers.EDT) {
    val toolWindow = restoreStoredTabs(storedTab1, storedTab2, storedTab3)

    withTerminalToolWindowManager(project) { manager ->
      val contents = toolWindow.contentManager.contents
      assertThat(contents.map { it.displayName }).containsExactly("Restored 1", "Restored 2", "Restored 3")
      assertThat(contents.map { it.getPendingTerminalTab() }).containsExactly(null, storedTab2, storedTab3)
      assertThat(contents.map { it.getTerminalTab() != null }).containsExactly(true, false, false)

      val first = manager.tabs.single()
      assertThat(first.content).isSameAs(contents[0])
      assertThat(first.view.title.userDefinedTitle).isEqualTo("Restored 1")
      assertThat(first.processOptions.shellCommand).containsExactly("/bin/zsh")
      assertThat(first.processOptions.workingDirectory).isEqualTo("/tmp/one")
      assertThat(first.processOptions.envVariables).containsEntry("FOO", "bar").hasSize(1)
      assertThat(first.processOptions.processType).isEqualTo(TerminalProcessType.SHELL)
    }
  }

  @Test
  fun `pending tabs stay in storage`(): Unit = runBlocking(Dispatchers.EDT) {
    val toolWindow = restoreStoredTabs(storedTab1, storedTab2, storedTab3)
    val storage = TerminalTabsStorage.getInstance(project)

    withTerminalToolWindowManager(project) { manager ->
      // Renaming the built tab makes the persistence store all tabs again.
      manager.tabs.single().view.title.change {
        userDefinedTitle = "Renamed 1"
      }

      awaitCondition("the renamed first tab should be persisted") { storage.getStoredTabs().firstOrNull()?.name == "Renamed 1" }
      assertThat(storage.getStoredTabs()).containsExactly(storedTab1.copy(name = "Renamed 1"), storedTab2, storedTab3)
      assertThat(toolWindow.contentManager.contents.map { it.getTerminalTab() != null }).containsExactly(true, false, false)
    }
  }

  @Test
  fun `a pending tab is built when it is selected`(): Unit = runBlocking(Dispatchers.EDT) {
    val viewsCreated = mutableListOf<TerminalView>()
    val tabsAdded = mutableListOf<TerminalToolWindowTab>()
    project.messageBus.connect(disposable).subscribe(TerminalTabsManagerListener.TOPIC, object : TerminalTabsManagerListener {
      override fun terminalViewCreated(view: TerminalView) {
        viewsCreated.add(view)
      }

      override fun tabAdded(tab: TerminalToolWindowTab) {
        tabsAdded.add(tab)
      }
    })
    val toolWindow = restoreStoredTabs(storedTab1, storedTab2, storedTab3)

    withTerminalToolWindowManager(project) { manager ->
      val contents = toolWindow.contentManager.contents
      val first = manager.tabs.single()
      assertThat(viewsCreated).containsExactly(first.view)
      assertThat(tabsAdded).containsExactly(first)

      toolWindow.contentManager.setSelectedContent(contents[2])

      val third = requireNotNull(contents[2].getTerminalTab()) { "The selected pending tab should be built" }
      assertThat(manager.tabs).containsExactly(first, third)
      assertThat(contents[2].getPendingTerminalTab()).isNull()
      assertThat(contents[2].displayName).isEqualTo("Restored 3")
      assertThat(third.view.title.defaultTitle).isEqualTo("Restored 3")
      assertThat(third.view.title.userDefinedTitle).isNull()
      assertThat(third.processOptions.shellCommand).containsExactly("/bin/bash")
      assertThat(third.processOptions.workingDirectory).isEqualTo("/tmp/three")
      assertThat(third.processOptions.processType).isEqualTo(TerminalProcessType.SHELL)
      assertThat(contents[1].getTerminalTab()).isNull()

      assertThat(viewsCreated).containsExactly(first.view, third.view)
      assertThat(tabsAdded).containsExactly(first, third)
    }
  }

  @Test
  fun `a change of a built pending tab is persisted`(): Unit = runBlocking(Dispatchers.EDT) {
    val toolWindow = restoreStoredTabs(storedTab1, storedTab2)
    val storage = TerminalTabsStorage.getInstance(project)

    withTerminalToolWindowManager(project) {
      val second = toolWindow.contentManager.contents[1]
      toolWindow.contentManager.setSelectedContent(second)
      awaitEarlierPersistencePasses()
      second.getTerminalTab()!!.view.title.change {
        userDefinedTitle = "Renamed 2"
      }

      awaitCondition("the renamed second tab should be persisted") { storage.getStoredTabs().getOrNull(1)?.name == "Renamed 2" }
      assertThat(storage.getStoredTabs()).containsExactly(storedTab1, storedTab2.copy(name = "Renamed 2", isUserDefinedName = true))
    }
  }

  @Test
  fun `renaming a pending tab builds it and persists the name`(): Unit = runBlocking(Dispatchers.EDT) {
    val toolWindow = restoreStoredTabs(storedTab1, storedTab2)
    val storage = TerminalTabsStorage.getInstance(project)

    withTerminalToolWindowManager(project) { manager ->
      val second = toolWindow.contentManager.contents[1]
      awaitEarlierPersistencePasses()
      TerminalRenameTabAction().applyContentDisplayName(second, project, "Renamed 2")

      assertThat(manager.tabs).hasSize(2)
      assertThat(second.getTerminalTab()?.view?.title?.userDefinedTitle).isEqualTo("Renamed 2")
      awaitCondition("the renamed second tab should be persisted") { storage.getStoredTabs().getOrNull(1)?.name == "Renamed 2" }
      assertThat(storage.getStoredTabs()).containsExactly(storedTab1, storedTab2.copy(name = "Renamed 2", isUserDefinedName = true))
    }
  }

  @Test
  fun `a pending tab can be moved to the editor and is built for its editor tab`(): Unit = runBlocking(Dispatchers.EDT) {
    val toolWindow = restoreStoredTabs(storedTab1, storedTab2)

    withTerminalToolWindowManager(project) { manager ->
      val first = manager.tabs.single()
      val second = toolWindow.contentManager.contents[1]
      val support = TerminalToolWindowEditorTabSupport()
      assertThat(support.canBeMovedToEditor(second)).isTrue()
      assertThat(TerminalInEditorSupport().canOpenInEditor(project, second)).isTrue()
      // The platform asks it before the presentation flow, so the editor tab is persistent.
      assertThat(TerminalToolWindowEditorTabPersistenceProvider().canSerialize(second)).isTrue()
      assertThat(second.getTerminalTab()).isNull()

      val presentation = support.getTabPresentationFlow(project, second).first()

      assertThat(presentation.title).isEqualTo("Restored 2")
      val tab = requireNotNull(second.getTerminalTab()) { "The pending tab should be built for its editor tab" }
      assertThat(second.getPendingTerminalTab()).isNull()
      assertThat(tab.view.title.defaultTitle).isEqualTo("Restored 2")
      assertThat(tab.processOptions.workingDirectory).isEqualTo("/tmp/two")
      assertThat(manager.tabs).containsExactly(first, tab)
    }
  }

  @Test
  fun `a closed pending tab is removed from storage`(): Unit = runBlocking(Dispatchers.EDT) {
    val viewsCreated = mutableListOf<TerminalView>()
    project.messageBus.connect(disposable).subscribe(TerminalTabsManagerListener.TOPIC, object : TerminalTabsManagerListener {
      override fun terminalViewCreated(view: TerminalView) {
        viewsCreated.add(view)
      }
    })
    val toolWindow = restoreStoredTabs(storedTab1, storedTab2, storedTab3)
    val storage = TerminalTabsStorage.getInstance(project)

    withTerminalToolWindowManager(project) {
      val contentManager = toolWindow.contentManager
      contentManager.removeContent(contentManager.contents[1], true)

      awaitCondition("the closed pending tab should be removed from storage") { storage.getStoredTabs().size == 2 }
      assertThat(storage.getStoredTabs()).containsExactly(storedTab1, storedTab3)
      assertThat(viewsCreated).hasSize(1)
    }
  }

  @Test
  fun `created and closed tabs are persisted to storage`() = runBlocking(Dispatchers.EDT) {
    val storage = TerminalTabsStorage.getInstance(project)
    storage.updateStoredTabs(emptyList())

    val toolWindow = registerTerminalToolWindow()
    // Installs the persistence that watches the tool window content manager.
    TerminalToolWindowInitializer.performInitialization(toolWindow)

    withTerminalToolWindowManager(project) { manager ->
      val tab = manager.createTabBuilder()
        .tabName("Persisted")
        .workingDirectory("/tmp/persist")
        .shellCommand(listOf("/bin/bash"))
        .requestFocus(false)
        .createTab()

      awaitCondition("the created tab should be persisted") { storage.getStoredTabs().size == 1 }
      val persisted = storage.getStoredTabs().single()
      assertThat(persisted.name).isEqualTo("Persisted")
      assertThat(persisted.isUserDefinedName).isFalse()
      assertThat(persisted.shellCommand).containsExactly("/bin/bash")
      assertThat(persisted.workingDirectory).isEqualTo("/tmp/persist")
      assertThat(persisted.processType).isEqualTo(TerminalProcessType.SHELL)

      manager.closeTab(tab)

      awaitCondition("the closed tab should be removed from storage") { storage.getStoredTabs().isEmpty() }
    }
  }

  @Test
  fun `a tab that opted out of restoring is not persisted`() = runBlocking(Dispatchers.EDT) {
    val storage = TerminalTabsStorage.getInstance(project)
    storage.updateStoredTabs(emptyList())

    val toolWindow = registerTerminalToolWindow()
    TerminalToolWindowInitializer.performInitialization(toolWindow)

    withTerminalToolWindowManager(project) { manager ->
      manager.createTabBuilder()
        .tabName("One-shot")
        .shellCommand(listOf("/bin/bash"))
        .restoreOnProjectReopen(false)
        .requestFocus(false)
        .createTab()

      // The second tab is the control: it is what proves persistence is installed and running, so the
      // absence of the first one is a decision rather than an update that never happened.
      manager.createTabBuilder()
        .tabName("Persisted")
        .shellCommand(listOf("/bin/bash"))
        .requestFocus(false)
        .createTab()

      awaitCondition("only the restorable tab should be persisted") {
        storage.getStoredTabs().map { it.name } == listOf("Persisted")
      }
      assertThat(manager.tabs).hasSize(2)
    }
  }

  @Test
  fun `tab rename is persisted to storage`() = runBlocking(Dispatchers.EDT) {
    val storage = TerminalTabsStorage.getInstance(project)
    storage.updateStoredTabs(emptyList())

    val toolWindow = registerTerminalToolWindow()
    TerminalToolWindowInitializer.performInitialization(toolWindow)

    withTerminalToolWindowManager(project) { manager ->
      val tab = manager.createTabBuilder()
        .tabName("Before")
        .requestFocus(false)
        .createTab()

      awaitCondition("the created tab should be persisted") { storage.getStoredTabs().size == 1 }

      tab.view.title.change {
        userDefinedTitle = "After"
      }

      awaitCondition("the renamed tab should be persisted with the user-defined name") {
        val stored = storage.getStoredTabs().singleOrNull()
        stored?.name == "After" && stored.isUserDefinedName
      }
    }
  }

  private suspend fun awaitCondition(message: String, timeout: Duration = 10.seconds, condition: () -> Boolean) {
    val satisfied = withTimeoutOrNull(timeout) {
      while (!condition()) {
        delay(50.milliseconds)
      }
      true
    } ?: false
    assertThat(satisfied).describedAs("$message (not satisfied within $timeout)").isTrue()
  }

  /**
   * The persistence stores the current state of all tabs in each pass.
   * So a pass that an earlier event requested can store a later change too.
   * Wait for these passes, so that only the change under test can make the next pass.
   * On a slow machine the wait can be too short. Then the test can pass without the code under test, but the wait cannot make it fail.
   */
  private suspend fun awaitEarlierPersistencePasses() {
    delay(1.seconds)
  }

  private fun registerTerminalToolWindow(): ToolWindow {
    return ToolWindowManager.getInstance(project)
      .registerToolWindow(RegisterToolWindowTask(id = TerminalToolWindowFactory.TOOL_WINDOW_ID))
  }

  /**
   * Stores [tabs], initializes the tool window, and waits until the tool window has a content for each stored tab.
   */
  private suspend fun restoreStoredTabs(vararg tabs: TerminalSessionPersistedTab): ToolWindow {
    // Restoring only happens for a trusted project with the reworked terminal enabled (new UI is on by default in tests).
    TerminalTestUtil.setTerminalEngineForTest(TerminalEngine.REWORKED, disposable)
    TrustedProjects.setProjectTrusted(project, true)
    TerminalTabsStorage.getInstance(project).updateStoredTabs(tabs.toList())

    val toolWindow = registerTerminalToolWindow()
    TerminalToolWindowInitializer.performInitialization(toolWindow)
    awaitCondition("${tabs.size} tabs should be restored") { toolWindow.contentManager.contentCount == tabs.size }
    return toolWindow
  }
}
