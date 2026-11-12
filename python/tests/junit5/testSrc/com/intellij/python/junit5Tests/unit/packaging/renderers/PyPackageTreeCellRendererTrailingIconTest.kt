// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.junit5Tests.unit.packaging.renderers

import com.intellij.idea.TestFor
import com.jetbrains.python.packaging.toolwindow.packages.tree.renderers.TrailingIconKind
import com.jetbrains.python.packaging.toolwindow.packages.tree.renderers.chooseInstallableTrailingIconKind
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

@TestFor(issues = ["PY-91529"])
internal class PyPackageTreeCellRendererTrailingIconTest {

  @Test
  fun `a running install outranks the hover state so the row shows a spinner, not an install icon`() {
    val kind = chooseInstallableTrailingIconKind(isInstalling = true, showActions = true)
    assertEquals(TrailingIconKind.PROGRESS, kind, "hovering a row whose install is running must not turn the spinner back into an action")
  }

  @Test
  fun `the spinner is shown even when the row is not hovered`() {
    val kind = chooseInstallableTrailingIconKind(isInstalling = true, showActions = false)
    assertEquals(TrailingIconKind.PROGRESS, kind, "progress must stay visible regardless of hover state")
  }

  @Test
  fun `hovering an idle row offers the install action`() {
    val kind = chooseInstallableTrailingIconKind(isInstalling = false, showActions = true)
    assertEquals(TrailingIconKind.ACTION, kind)
  }

  @Test
  fun `no icon when nothing is running and the row is not hovered`() {
    val kind = chooseInstallableTrailingIconKind(isInstalling = false, showActions = false)
    assertNull(kind)
  }
}
