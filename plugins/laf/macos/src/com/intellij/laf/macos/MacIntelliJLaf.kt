@file:Suppress("ReplacePutWithAssignment")

package com.intellij.laf.macos

import com.intellij.ide.ui.laf.IntelliJLaf
import com.intellij.util.ui.StartupUiUtil
import javax.swing.UIDefaults
import javax.swing.UIManager

internal class MacIntelliJLaf : IntelliJLaf() {
  init {
    putUserData(StartupUiUtil.PLUGGABLE_LAF_KEY, name)
  }

  override fun getName() = MacLafProvider.LAF_NAME

  override fun loadDefaults(defaults: UIDefaults) {
    super.loadDefaults(defaults)

    for ((key, value) in baseDefaults) {
      if (key is String && key.endsWith(".selectionBackground")) {
        defaults.put(key, value)
      }
    }
  }

  override fun getPrefix() = "com/intellij/ide/ui/laf/intellijlaf"

  override fun getSystemPrefix() = "com/intellij/laf/macos/macintellijlaf"

  companion object {
    fun isMacLaf() = UIManager.getLookAndFeel() is MacIntelliJLaf
  }
}