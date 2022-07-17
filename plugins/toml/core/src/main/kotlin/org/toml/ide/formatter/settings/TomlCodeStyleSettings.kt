/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.toml.ide.formatter.settings

import com.intellij.psi.codeStyle.CodeStyleSettings
import com.intellij.psi.codeStyle.CustomCodeStyleSettings

class TomlCodeStyleSettings(container: CodeStyleSettings) :
    CustomCodeStyleSettings(TomlCodeStyleSettings::class.java.simpleName, container)
