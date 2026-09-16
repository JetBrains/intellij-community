// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.junit5Tests.unit.pytools

import com.intellij.idea.TestFor
import com.intellij.python.lsp.core.LspPackageAction
import com.intellij.python.lsp.core.lspPackageAction
import com.intellij.python.lsp.core.lspPackageVersionAction
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Guards the rule that `LspPackageListener` applies to a package change.
 *
 * The listener used to treat the first answer as a change. On a restart the first answer for a tool that runs
 * from `PATH` or through `uvx` is "not a package", so the listener stopped the server that the reopened editors
 * had just started (PY-92163).
 */
internal class PyLspPackageActionTest {
  @Test
  fun `a first not-a-package answer stops no server`() {
    assertEquals(LspPackageAction.NONE, lspPackageAction(previous = null, current = false))
  }

  @Test
  fun `a first a-package answer starts the server`() {
    assertEquals(LspPackageAction.START, lspPackageAction(previous = null, current = true))
  }

  @Test
  fun `an install starts the server and an uninstall stops it`() {
    assertEquals(LspPackageAction.START, lspPackageAction(previous = false, current = true))
    assertEquals(LspPackageAction.STOP, lspPackageAction(previous = true, current = false))
  }

  @Test
  fun `an unchanged answer does nothing`() {
    assertEquals(LspPackageAction.NONE, lspPackageAction(previous = false, current = false))
    assertEquals(LspPackageAction.NONE, lspPackageAction(previous = true, current = true))
  }

  @Test
  @TestFor(issues = ["PY-92008"])
  fun `an upgrade in place restarts the server`() {
    assertEquals(LspPackageAction.UPGRADE, lspPackageVersionAction(seenBefore = true, previous = "0.40.0", current = "1.1.1"))
  }

  @Test
  @TestFor(issues = ["PY-92008"])
  fun `the same version does nothing`() {
    assertEquals(LspPackageAction.NONE, lspPackageVersionAction(seenBefore = true, previous = "1.1.1", current = "1.1.1"))
    assertEquals(LspPackageAction.NONE, lspPackageVersionAction(seenBefore = true, previous = null, current = null))
  }

  @Test
  @TestFor(issues = ["PY-92008"])
  fun `a version that arrives or leaves starts or stops the server`() {
    assertEquals(LspPackageAction.START, lspPackageVersionAction(seenBefore = true, previous = null, current = "1.1.1"))
    assertEquals(LspPackageAction.STOP, lspPackageVersionAction(seenBefore = true, previous = "1.1.1", current = null))
  }

  @Test
  @TestFor(issues = ["PY-92008", "PY-92163"])
  fun `the first version answer follows the first package answer`() {
    assertEquals(LspPackageAction.START, lspPackageVersionAction(seenBefore = false, previous = null, current = "1.1.1"))
    assertEquals(LspPackageAction.NONE, lspPackageVersionAction(seenBefore = false, previous = null, current = null))
  }
}
