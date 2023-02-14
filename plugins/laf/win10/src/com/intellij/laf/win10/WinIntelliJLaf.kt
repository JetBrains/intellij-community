package com.intellij.laf.win10

import com.intellij.ide.ui.laf.IntelliJLaf
import com.intellij.ide.ui.laf.MenuArrowIcon
import com.intellij.util.ui.UIUtil
import javax.swing.UIDefaults

internal class WinIntelliJLaf : IntelliJLaf() {
  init {
    putUserData(UIUtil.PLUGGABLE_LAF_KEY, name)
  }

  override fun getName() = WinLafProvider.LAF_NAME

  override fun getPrefix(): String {
    return "com/intellij/laf/win10/win10intellijlaf"
  }

  override fun getSystemPrefix(): String? {
    return null
  }

  override fun loadDefaults(defaults: UIDefaults) {
    super.loadDefaults(defaults)
    defaults["ClassLoader"] = javaClass.classLoader
    defaults["Menu.arrowIcon"] = Win10MenuArrowIcon()
  }

  private class Win10MenuArrowIcon :
    MenuArrowIcon({ WinIconLookup.getIcon(name = MENU_TRIANGLE_ICON_NAME) },
                  { WinIconLookup.getIcon(name = MENU_TRIANGLE_ICON_NAME, selected = true) },
                  { WinIconLookup.getIcon(name = MENU_TRIANGLE_ICON_NAME, enabled = false) })

  companion object {
    const val MENU_TRIANGLE_ICON_NAME = "menuTriangle"
  }
}