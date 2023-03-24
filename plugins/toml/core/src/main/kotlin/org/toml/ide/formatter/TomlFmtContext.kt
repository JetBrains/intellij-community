/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.toml.ide.formatter

import com.intellij.formatting.SpacingBuilder
import com.intellij.psi.codeStyle.CodeStyleSettings
import com.intellij.psi.codeStyle.CommonCodeStyleSettings
import org.toml.ide.formatter.impl.createSpacingBuilder
import org.toml.ide.formatter.settings.TomlCodeStyleSettings
import org.toml.lang.TomlLanguage

data class TomlFmtContext(
  val commonSettings: CommonCodeStyleSettings,
  val tomlSettings: TomlCodeStyleSettings,
  val spacingBuilder: SpacingBuilder
) {
  companion object {
    fun create(settings: CodeStyleSettings): TomlFmtContext {
      val commonSettings = settings.getCommonSettings(TomlLanguage)
      val tomlSettings = settings.toml
      return TomlFmtContext(commonSettings, tomlSettings, createSpacingBuilder(commonSettings))
    }
  }
}
