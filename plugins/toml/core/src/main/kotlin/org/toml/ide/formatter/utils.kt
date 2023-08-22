/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.toml.ide.formatter

import com.intellij.psi.codeStyle.CodeStyleSettings
import org.toml.ide.formatter.settings.TomlCodeStyleSettings

val CodeStyleSettings.toml: TomlCodeStyleSettings
    get() = getCustomSettings(TomlCodeStyleSettings::class.java)
