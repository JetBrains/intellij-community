package com.intellij.laf.win10

import com.intellij.ide.ui.laf.IntelliJLaf

class WinIntelliJLaf : IntelliJLaf() {
  override fun getName(): String {
    return WinLafProvider.LAF_NAME
  }

  override fun getPrefix(): String {
    return "/win10intellijlaf"
  }

  override fun getSystemPrefix(): String? {
    return null
  }
}