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

  companion object {
    fun isMacLaf() = UIManager.getLookAndFeel() is MacIntelliJLaf
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

  override val prefix: String
    get() = "com/intellij/ide/ui/laf/intellijlaf"
  override val systemPrefix: String
    get() = "com/intellij/laf/macos/macintellijlaf"
}