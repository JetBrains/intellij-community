// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.junit5Tests.unit

import com.intellij.pycharm.community.ide.impl.configuration.interpreter.PySdkName
import com.intellij.pycharm.community.ide.impl.configuration.interpreter.pickSelection
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/** Pure-state tests for `PyAllInterpretersConfigurable.pickSelection` — typed on [PySdkName]. */
class PyAllInterpretersSelectionTest {

  private val py311 = PySdkName("Python 3.11")
  private val py312 = PySdkName("Python 3.12")
  private val py310 = PySdkName("Python 3.10")

  @Test
  fun `picks the row matching previously selected name`() {
    assertEquals(py312, pickSelection(candidates = listOf(py311, py312), previouslySelected = py312))
  }

  @Test
  fun `falls back to the first row when nothing matches`() {
    assertEquals(py311, pickSelection(candidates = listOf(py311, py312), previouslySelected = py310))
  }

  @Test
  fun `falls back to the first row when previously selected is null`() {
    assertEquals(py311, pickSelection(candidates = listOf(py311, py312), previouslySelected = null))
  }

  @Test
  fun `returns null for an empty list`() {
    assertNull(pickSelection(candidates = emptyList(), previouslySelected = py312))
  }
}
