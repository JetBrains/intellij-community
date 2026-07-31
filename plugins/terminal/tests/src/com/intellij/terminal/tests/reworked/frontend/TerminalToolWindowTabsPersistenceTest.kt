// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.tests.reworked.frontend

import com.intellij.ide.trustedProjects.TrustedProjects
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.RegisterToolWindowTask
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.terminal.tests.reworked.util.TerminalTestUtil
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.disposableFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
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

  @Test
  fun `stored tabs are restored on tool window initialization`(): Unit = runBlocking(Dispatchers.EDT) {
    // Restoring only happens for a trusted project with the reworked terminal enabled (new UI is on by default in tests).
    TerminalTestUtil.setTerminalEngineForTest(TerminalEngine.REWORKED, disposable)
    TrustedProjects.setProjectTrusted(project, true)

    TerminalTabsStorage.getInstance(project).updateStoredTabs(listOf(
      TerminalSessionPersistedTab(
        name = "Restored 1",
        isUserDefinedName = true,
        shellCommand = listOf("/bin/zsh"),
        workingDirectory = "/tmp/one",
        envVariables = mapOf("FOO" to "bar"),
        processType = TerminalProcessType.SHELL,
      ),
      TerminalSessionPersistedTab(
        name = "Restored 2",
        isUserDefinedName = false,
        shellCommand = null,
        workingDirectory = "/tmp/two",
        envVariables = emptyMap(),
        processType = TerminalProcessType.NON_SHELL,
      ),
    ))

    val toolWindow = registerTerminalToolWindow()
    TerminalToolWindowInitializer.performInitialization(toolWindow)

    withTerminalToolWindowManager(project) { manager ->
      awaitCondition("2 tabs should be restored") { manager.tabs.size == 2 }

      val tabs = manager.tabs
      val first = tabs.single { it.view.title.userDefinedTitle == "Restored 1" }
      assertThat(first.processOptions.shellCommand).containsExactly("/bin/zsh")
      assertThat(first.processOptions.workingDirectory).isEqualTo("/tmp/one")
      assertThat(first.processOptions.envVariables).containsEntry("FOO", "bar").hasSize(1)
      assertThat(first.processOptions.processType).isEqualTo(TerminalProcessType.SHELL)

      val second = tabs.single { it.view.title.defaultTitle == "Restored 2" }
      assertThat(second.view.title.userDefinedTitle).isNull()
      assertThat(second.processOptions.workingDirectory).isEqualTo("/tmp/two")
      assertThat(second.processOptions.processType).isEqualTo(TerminalProcessType.NON_SHELL)
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

  private fun registerTerminalToolWindow(): ToolWindow {
    return ToolWindowManager.getInstance(project)
      .registerToolWindow(RegisterToolWindowTask(id = TerminalToolWindowFactory.TOOL_WINDOW_ID))
  }
}
