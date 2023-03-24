/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.toml.ide.formatter.settings

import com.intellij.application.options.CodeStyleAbstractConfigurable
import com.intellij.application.options.CodeStyleAbstractPanel
import com.intellij.application.options.IndentOptionsEditor
import com.intellij.application.options.SmartIndentOptionsEditor
import com.intellij.lang.Language
import com.intellij.psi.codeStyle.*
import org.toml.ide.formatter.settings.TomlCodeStyleSettings.Companion.INDENT_TABLE_KEYS_DEFAULT
import org.toml.lang.TomlLanguage
import javax.swing.JCheckBox

class TomlLanguageCodeStyleSettingsProvider : LanguageCodeStyleSettingsProvider() {
  override fun getLanguage(): Language = TomlLanguage

  override fun createCustomSettings(settings: CodeStyleSettings): CustomCodeStyleSettings =
    TomlCodeStyleSettings(settings)

  override fun createConfigurable(
    baseSettings: CodeStyleSettings,
    modelSettings: CodeStyleSettings
  ): CodeStyleConfigurable {
    return object : CodeStyleAbstractConfigurable(baseSettings, modelSettings, configurableDisplayName) {
      override fun createPanel(settings: CodeStyleSettings): CodeStyleAbstractPanel =
        TomlCodeStyleMainPanel(currentSettings, settings)
    }
  }

  override fun getCodeSample(settingsType: SettingsType): String =
    when (settingsType) {
      SettingsType.INDENT_SETTINGS -> INDENT_SAMPLE
      else -> ""
    }

  override fun getIndentOptionsEditor(): IndentOptionsEditor = TomlIndentOptionsEditor()
}


private class TomlIndentOptionsEditor: SmartIndentOptionsEditor() {
  private lateinit var indentTableKeys: JCheckBox

  override fun addComponents() {
    super.addComponents()
    indentTableKeys = JCheckBox("Indent table keys")
    add(indentTableKeys)
  }

  override fun isModified(settings: CodeStyleSettings?, options: CommonCodeStyleSettings.IndentOptions?): Boolean {
    val modified = super.isModified(settings, options)
    val indentTableKeysOption = getTomlSettings(settings)?.INDENT_TABLE_KEYS ?: INDENT_TABLE_KEYS_DEFAULT
    return modified || IndentOptionsEditor.isFieldModified(indentTableKeys, indentTableKeysOption)
  }

  override fun apply(settings: CodeStyleSettings?, options: CommonCodeStyleSettings.IndentOptions?) {
    super.apply(settings, options)
    getTomlSettings(settings)?.INDENT_TABLE_KEYS = indentTableKeys.isSelected
  }

  override fun reset(settings: CodeStyleSettings, options: CommonCodeStyleSettings.IndentOptions) {
    super.reset(settings, options)
    indentTableKeys.isSelected = getTomlSettings(settings)?.INDENT_TABLE_KEYS ?: INDENT_TABLE_KEYS_DEFAULT
  }

  override fun setEnabled(enabled: Boolean) {
    super.setEnabled(enabled)
    indentTableKeys.isEnabled = enabled
  }

  override fun setVisible(visible: Boolean) {
    super.setVisible(visible)
    indentTableKeys.isVisible = visible
  }

  companion object {
    private fun getTomlSettings(settings: CodeStyleSettings?): TomlCodeStyleSettings? =
      settings?.getCustomSettings(TomlCodeStyleSettings::class.java)
  }
}
private fun sample(@org.intellij.lang.annotations.Language("TOML") code: String) = code.trim()

private val INDENT_SAMPLE = sample("""
[config]
foo = "bar"
items = [
    "foo",
    "bar"
]
""")
