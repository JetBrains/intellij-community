package com.intellij.lang.properties

import com.intellij.lang.properties.psi.codeStyle.PropertiesCodeStyleSettings
import com.intellij.psi.codeStyle.CodeStyleSettingsCustomDataSynchronizer

class PropertiesCodeStyleSettingsCustomDataSynchronizer : CodeStyleSettingsCustomDataSynchronizer<PropertiesCodeStyleSettings>() {
  override val language: PropertiesLanguage get() = PropertiesLanguage.INSTANCE

  override val customCodeStyleSettingsClass: Class<PropertiesCodeStyleSettings> get() = PropertiesCodeStyleSettings::class.java
}