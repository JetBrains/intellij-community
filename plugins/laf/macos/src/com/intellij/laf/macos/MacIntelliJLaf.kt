package com.intellij.laf.macos

import com.intellij.ide.ui.laf.IntelliJLaf
import javax.swing.UIManager

class MacIntelliJLaf : IntelliJLaf() {
  override fun getName(): String {
    return MacLafProvider.LAF_NAME
  }

  override fun getPrefix(): String = "/com/intellij/ide/ui/laf/intellijlaf"

  override fun getSystemPrefix(): String? = "macintellijlaf"

  companion object {
    fun isMacLaf() = UIManager.getLookAndFeel() is MacIntelliJLaf
  }
}