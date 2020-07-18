/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.toml.ide.formatter.settings

import com.intellij.application.options.IndentOptionsEditor
import com.intellij.application.options.SmartIndentOptionsEditor
import com.intellij.lang.Language
import com.intellij.psi.codeStyle.LanguageCodeStyleSettingsProvider
import org.toml.lang.TomlLanguage

class TomlLanguageCodeStyleSettingsProvider : LanguageCodeStyleSettingsProvider() {
    override fun getLanguage(): Language = TomlLanguage
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
