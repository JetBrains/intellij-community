package com.intellij.sh.codeStyle

import com.intellij.psi.codeStyle.CodeStyleSettingsCustomDataSynchronizer
import com.intellij.sh.ShLanguage

class ShCodeStyleSettingsCustomDataSynchronizer : CodeStyleSettingsCustomDataSynchronizer<ShCodeStyleSettings>() {
  override val language get() = ShLanguage.INSTANCE
  override val customCodeStyleSettingsClass get() = ShCodeStyleSettings::class.java
}