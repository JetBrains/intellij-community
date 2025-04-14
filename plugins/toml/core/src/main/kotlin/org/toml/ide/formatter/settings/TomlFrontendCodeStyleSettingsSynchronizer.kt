package org.toml.ide.formatter.settings

import com.intellij.lang.Language
import com.intellij.psi.codeStyle.CodeStyleSettingsCustomDataSynchronizer
import org.toml.lang.TomlLanguage

class TomlCodeStyleSettingsSynchronizer : CodeStyleSettingsCustomDataSynchronizer<TomlCodeStyleSettings>() {
  override val language: Language
    get() = TomlLanguage

  override val customCodeStyleSettingsClass: Class<TomlCodeStyleSettings>
    get() = TomlCodeStyleSettings::class.java
}