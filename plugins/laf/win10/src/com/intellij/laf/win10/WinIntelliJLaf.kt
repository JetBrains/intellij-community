@file:Suppress("ReplacePutWithAssignment")

package com.intellij.laf.win10

import com.intellij.ide.ui.laf.IntelliJLaf
import com.intellij.ide.ui.laf.MenuArrowIcon
import com.intellij.util.ui.StartupUiUtil
import javax.swing.UIDefaults

private const val MENU_TRIANGLE_ICON_NAME = "menuTriangle"

internal class WinIntelliJLaf : IntelliJLaf() {
  init {
    putUserData(StartupUiUtil.PLUGGABLE_LAF_KEY, name)
  }

  override fun getName() = WinLafProvider.LAF_NAME

  override val prefix: String
    get() = "com/intellij/laf/win10/win10intellijlaf"
  override val systemPrefix: String?
    get() = null

  override fun loadDefaults(defaults: UIDefaults) {
    super.loadDefaults(defaults)

    defaults.put("Menu.arrowIcon", Win10MenuArrowIcon())
  }
}

private class Win10MenuArrowIcon :
  MenuArrowIcon(icon = { WinIconLookup.getIcon(name = MENU_TRIANGLE_ICON_NAME) },
                selectedIcon = { WinIconLookup.getIcon(name = MENU_TRIANGLE_ICON_NAME, selected = true) },
                disabledIcon = { WinIconLookup.getIcon(name = MENU_TRIANGLE_ICON_NAME, enabled = false) })
