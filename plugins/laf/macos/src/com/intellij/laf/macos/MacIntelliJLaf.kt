package com.intellij.laf.macos

import com.intellij.ide.ui.laf.IntelliJLaf

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
}