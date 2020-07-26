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
import com.intellij.psi.codeStyle.CodeStyleConfigurable
import com.intellij.psi.codeStyle.CodeStyleSettings
import com.intellij.psi.codeStyle.CustomCodeStyleSettings
import com.intellij.psi.codeStyle.LanguageCodeStyleSettingsProvider
import org.toml.lang.TomlLanguage

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

    override fun getIndentOptionsEditor(): IndentOptionsEditor? = SmartIndentOptionsEditor()
}

private fun sample(@org.intellij.lang.annotations.Language("TOML") code: String) = code.trim()

private val INDENT_SAMPLE = sample("""
[config]
items = [
    "foo",
    "bar"
]
""")
