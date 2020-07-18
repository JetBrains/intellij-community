/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.toml.ide.formatter.settings

import com.intellij.application.options.CodeStyleAbstractConfigurable
import com.intellij.application.options.TabbedLanguageCodeStylePanel
import com.intellij.openapi.options.Configurable
import com.intellij.psi.codeStyle.CodeStyleSettings
import com.intellij.psi.codeStyle.CodeStyleSettingsProvider
import com.intellij.psi.codeStyle.CustomCodeStyleSettings
import org.toml.lang.TomlLanguage

class TomlCodeStyleSettingsProvider : CodeStyleSettingsProvider() {
    override fun createCustomSettings(settings: CodeStyleSettings): CustomCodeStyleSettings = TomlCodeStyleSettings(settings)

    override fun getConfigurableDisplayName(): String = TomlLanguage.displayName

    override fun createSettingsPage(settings: CodeStyleSettings, originalSettings: CodeStyleSettings): Configurable =
        object : CodeStyleAbstractConfigurable(settings, originalSettings, configurableDisplayName) {
            override fun createPanel(settings: CodeStyleSettings) = TomlCodeStyleMainPanel(currentSettings, settings)
            override fun getHelpTopic() = null
        }

    private class TomlCodeStyleMainPanel(currentSettings: CodeStyleSettings, settings: CodeStyleSettings) :
        TabbedLanguageCodeStylePanel(TomlLanguage, currentSettings, settings) {

        override fun initTabs(settings: CodeStyleSettings) {
            addIndentOptionsTab(settings)
        }
    }
}
