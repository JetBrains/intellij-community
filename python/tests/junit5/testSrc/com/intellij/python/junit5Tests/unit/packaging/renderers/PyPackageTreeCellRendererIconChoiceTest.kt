// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.junit5Tests.unit.packaging.renderers

import com.intellij.util.ui.EmptyIcon
import com.jetbrains.python.packaging.toolwindow.packages.tree.renderers.chooseInlineChangeVersionIcon
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import javax.swing.Icon

internal class PyPackageTreeCellRendererIconChoiceTest {

  private val updateIcon: Icon = EmptyIcon.ICON_8
  private val defaultIcon: Icon = EmptyIcon.ICON_16

  @Test
  fun `local install hides the inline icon even when an update is available`() {
    val icon = chooseInlineChangeVersionIcon(
      isLocalInstall = true, hasUpdate = true, showActions = true,
      updateAvailableIcon = updateIcon, defaultActionIcon = defaultIcon,
    )
    assertNull(icon, "local installs must not show the inline change-version icon")
  }

  @Test
  fun `outstanding update wins over the neutral hover icon`() {
    val icon = chooseInlineChangeVersionIcon(
      isLocalInstall = false, hasUpdate = true, showActions = true,
      updateAvailableIcon = updateIcon, defaultActionIcon = defaultIcon,
    )
    assertEquals(updateIcon, icon)
  }

  @Test
  fun `update icon shown even when the row is not hovered`() {
    val icon = chooseInlineChangeVersionIcon(
      isLocalInstall = false, hasUpdate = true, showActions = false,
      updateAvailableIcon = updateIcon, defaultActionIcon = defaultIcon,
    )
    assertEquals(updateIcon, icon, "an update notification must stay visible regardless of hover state")
  }

  @Test
  fun `neutral icon is shown only while the row is hovered`() {
    val icon = chooseInlineChangeVersionIcon(
      isLocalInstall = false, hasUpdate = false, showActions = true,
      updateAvailableIcon = updateIcon, defaultActionIcon = defaultIcon,
    )
    assertEquals(defaultIcon, icon)
  }

  @Test
  fun `no icon when there is nothing to do`() {
    val icon = chooseInlineChangeVersionIcon(
      isLocalInstall = false, hasUpdate = false, showActions = false,
      updateAvailableIcon = updateIcon, defaultActionIcon = defaultIcon,
    )
    assertNull(icon)
  }
}
