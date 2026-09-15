// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.junit5Tests.unit

import com.intellij.ide.ui.icons.rpcId
import com.intellij.python.sdk.common.PyInterpreterRef
import com.intellij.python.sdk.common.evolution.PyInterpreterDto
import com.intellij.python.sdk.frontend.evolution.interpreterRowsChanged
import com.jetbrains.python.allure.Layers
import com.jetbrains.python.allure.Subsystems
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import javax.swing.ImageIcon

/**
 * When the "Associated" submenu has to be rebuilt from a fresh read.
 *
 * The widget keeps the rows it already shows and drops a fresh read that draws the same, so opening the popup does
 * not repaint on every look.
 *
 * Two of these prove the fix. The comparison used to weigh the selection ref alone, and an environment recreated on
 * another Python keeps its SDK name, so the refs still matched, the fresh read was dropped, and the submenu went on
 * naming a version that was gone. Restore that comparison and `a new version changes the rows` and `a new path
 * changes the rows` both fail.
 *
 * The other two hold what already worked, and pass either way. `a different set changes the rows` passes on the ref
 * alone, because adding or removing a row changes the list of refs. `an unchanged list is unchanged` guards the one
 * field the comparison must keep ignoring. Neither is evidence for the fix.
 */
@Subsystems.Interpreters
@Layers.Functional
class PyEvoAssociatedRowsTest {
  @Test
  @DisplayName("a recreated environment changes the rows, though its SDK name does not")
  fun `a new version changes the rows`() {
    val shown = listOf(row(".venv", ".venv [3.14.5]"))
    val fresh = listOf(row(".venv", ".venv [3.13.1]"))

    assertTrue(interpreterRowsChanged(shown, fresh), "A row naming another version must repaint the submenu")
  }

  @Test
  @DisplayName("the same rows do not repaint, though every icon is a fresh instance")
  fun `an unchanged list is unchanged`() {
    val shown = listOf(row(".venv", ".venv [3.14.5]"), row("other", "other [3.12.0]"))
    val fresh = listOf(row(".venv", ".venv [3.14.5]"), row("other", "other [3.12.0]"))

    // Every row carries a fresh IconId, which is what a re-read produces. Weighing it would repaint on every look.
    assertTrue(shown.zip(fresh).none { (a, b) -> a.icon == b.icon }, "Expected the icons to differ, or this proves nothing")
    assertFalse(interpreterRowsChanged(shown, fresh), "An unchanged list must not repaint the submenu")
  }

  @Test
  @DisplayName("a moved environment changes the rows, though its name and version do not")
  fun `a new path changes the rows`() {
    val shown = listOf(row(".venv", ".venv [3.14.5]", path = "/old/.venv/bin/python"))
    val fresh = listOf(row(".venv", ".venv [3.14.5]", path = "/new/.venv/bin/python"))

    assertTrue(interpreterRowsChanged(shown, fresh), "A row naming another path must repaint the submenu")
  }

  @Test
  @DisplayName("adding and removing a row changes the rows")
  fun `a different set changes the rows`() {
    val one = listOf(row(".venv", ".venv [3.14.5]"))
    val two = listOf(row(".venv", ".venv [3.14.5]"), row("other", "other [3.12.0]"))

    assertTrue(interpreterRowsChanged(one, two), "An added row must repaint the submenu")
    assertTrue(interpreterRowsChanged(two, one), "A removed row must repaint the submenu")
  }

  private fun row(sdkName: String, title: String, path: String = "/envs/$sdkName/bin/python") = PyInterpreterDto(
    title = title,
    description = path,
    // A fresh IconId every time, which is what a re-read produces.
    icon = ImageIcon().rpcId(),
    ref = PyInterpreterRef.ExistingSdk(sdkName),
  )
}
