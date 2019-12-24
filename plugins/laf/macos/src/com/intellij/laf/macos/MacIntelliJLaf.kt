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
  }

  override fun getPrefix(): String = "/com/intellij/ide/ui/laf/intellijlaf"

  override fun getSystemPrefix(): String? = "macintellijlaf"

  companion object {
    fun isMacLaf() = UIManager.getLookAndFeel() is MacIntelliJLaf
  }
}