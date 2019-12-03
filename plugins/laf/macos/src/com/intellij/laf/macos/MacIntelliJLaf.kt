package com.intellij.laf.macos

import com.intellij.ide.ui.laf.IntelliJLaf
import javax.swing.UIManager

class MacIntelliJLaf : IntelliJLaf() {
  override fun getName(): String {
    return MacLafProvider.LAF_NAME
  }

  override fun getPrefix(): String {
    return "/macintellijlaf"
  }

  override fun getSystemPrefix(): String? {
    return null
  }

  companion object {
    fun isMacLaf() = UIManager.getLookAndFeel() is MacIntelliJLaf
  }
}