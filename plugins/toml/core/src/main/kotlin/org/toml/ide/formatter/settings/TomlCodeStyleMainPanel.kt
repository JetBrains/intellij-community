/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.toml.ide.formatter.settings

import com.intellij.application.options.TabbedLanguageCodeStylePanel
import com.intellij.psi.codeStyle.CodeStyleSettings
import org.toml.lang.TomlLanguage

internal class TomlCodeStyleMainPanel(currentSettings: CodeStyleSettings, settings: CodeStyleSettings) :
    TabbedLanguageCodeStylePanel(TomlLanguage, currentSettings, settings) {

    override fun initTabs(settings: CodeStyleSettings) {
        addIndentOptionsTab(settings)
    }
}
