package com.intellij.laf.macos

import com.intellij.ide.ui.laf.IntelliJLaf
import com.intellij.util.ui.UIUtil
import javax.swing.UIDefaults
import javax.swing.UIManager

class MacIntelliJLaf : IntelliJLaf() {
  init {
    putUserData(UIUtil.PLUGGABLE_LAF_KEY, name)
  }

  override fun getName(): String {
    return MacLafProvider.LAF_NAME
  }

  override fun loadDefaults(defaults: UIDefaults) {
    super.loadDefaults(defaults)
    defaults["ClassLoader"] = javaClass.classLoader
    for ((key,value) in baseDefaults) {
      if (key is String && key.endsWith(".selectionBackground")) {
        defaults[key] = value
      }
    }
  }

  override fun getPrefix(): String = "com/intellij/ide/ui/laf/intellijlaf"

  override fun getSystemPrefix(): String = "com/intellij/laf/macos/macintellijlaf"

  companion object {
    fun isMacLaf() = UIManager.getLookAndFeel() is MacIntelliJLaf
  }
}