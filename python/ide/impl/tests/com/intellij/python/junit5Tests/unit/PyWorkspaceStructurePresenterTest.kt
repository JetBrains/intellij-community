// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.junit5Tests.unit

import com.intellij.pycharm.community.ide.impl.configuration.interpreter.PyWorkspaceStructurePresenter
import com.intellij.pycharm.community.ide.impl.configuration.interpreter.PyWorkspaceStructurePresenter.ToggleOutcome
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

internal class PyWorkspaceStructurePresenterTest {

  @Test
  fun `true to false is ToggledOff`() {
    assertSame(ToggleOutcome.ToggledOff, presenter().decideToggleOutcome(wasEnabled = true, nowEnabled = false))
  }

  @Test
  fun `false to true is ToggledOn`() {
    assertSame(ToggleOutcome.ToggledOn, presenter().decideToggleOutcome(wasEnabled = false, nowEnabled = true))
  }

  @Test
  fun `true to true is NoChange`() {
    assertSame(ToggleOutcome.NoChange, presenter().decideToggleOutcome(wasEnabled = true, nowEnabled = true))
  }

  @Test
  fun `false to false is NoChange`() {
    assertSame(ToggleOutcome.NoChange, presenter().decideToggleOutcome(wasEnabled = false, nowEnabled = false))
  }

  @Test
  fun `pipeline runs disposeUi before cleanup`() = runBlocking {
    val events = mutableListOf<String>()
    presenter().runToggledOffPipelineCore(
      disposeUi = { events += "disposeUi" },
      runCleanup = { events += "cleanup" },
    )
    assertEquals(listOf("disposeUi", "cleanup"), events)
  }

  private fun presenter() = PyWorkspaceStructurePresenter()
}
