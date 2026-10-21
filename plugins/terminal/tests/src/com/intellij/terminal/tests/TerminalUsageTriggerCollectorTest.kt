// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.tests

import com.intellij.ide.actions.ToolWindowTabLocation
import com.intellij.internal.statistic.FUCollectorTestCase
import com.intellij.openapi.Disposable
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.junit5.fixture.projectFixture
import org.jetbrains.plugins.terminal.fus.TerminalUsageTriggerCollector
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

@TestApplication
internal class TerminalUsageTriggerCollectorTest {
  companion object {
    private val projectFixture = projectFixture()
  }

  private val project get() = projectFixture.get()

  @Test
  fun `command executed reports the tab location when it is known`(@TestDisposable disposable: Disposable) {
    val events = FUCollectorTestCase.collectLogEvents(disposable) {
      TerminalUsageTriggerCollector.triggerCommandStarted(project, "git status", false, ToolWindowTabLocation.TOOL_WINDOW)
    }

    val data = events.single { it.event.id == "terminal.command.executed" }.event.data
    assertEquals("git", data["command"])
    assertEquals(false, data["new_terminal"])
    assertEquals("TOOL_WINDOW", data["toolwindow_tab_location"])
  }

  @Test
  fun `command executed omits the tab location when it is unknown`(@TestDisposable disposable: Disposable) {
    val events = FUCollectorTestCase.collectLogEvents(disposable) {
      TerminalUsageTriggerCollector.triggerCommandStarted(project, "git status", true, null)
    }

    val data = events.single { it.event.id == "terminal.command.executed" }.event.data
    assertEquals(true, data["new_terminal"])
    assertFalse(data.containsKey("toolwindow_tab_location"))
  }
}
