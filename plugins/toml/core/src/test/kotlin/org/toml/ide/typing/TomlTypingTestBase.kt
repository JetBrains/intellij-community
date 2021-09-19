/*
* Use of this source code is governed by the MIT license that can be
* found in the LICENSE file.
*/

package org.toml.ide.typing

import com.intellij.application.options.CodeStyle
import com.intellij.psi.codeStyle.CodeStyleSettings
import org.intellij.lang.annotations.Language
import org.toml.TomlTestBase
import org.toml.ide.formatter.toml
import kotlin.reflect.KMutableProperty0

abstract class TomlTypingTestBase : TomlTestBase() {
  protected fun doTestByText(@Language("TOML") before: String, @Language("TOML") after: String, c: Char = '\n') =
    checkByText(before.trimIndent(), after.trimIndent()) {
      myFixture.type(c)
    }

  protected fun doOptionTest(
    optionProperty: KMutableProperty0<Boolean>,
    @Language("TOML") before: String,
    @Language("TOML") afterOn: String = before,
    @Language("TOML") afterOff: String = before,
    c: Char = '\n'
  ) {
    val initialValue = optionProperty.get()
    optionProperty.set(true)
    try {
      doTestByText(before.trimIndent(), afterOn.trimIndent(), c)
      optionProperty.set(false)
      doTestByText(before.trimIndent(), afterOff.trimIndent(), c)
    } finally {
      optionProperty.set(initialValue)
    }
  }

  private fun commonSettings(): CodeStyleSettings = CodeStyle.getSettings(project)
  protected fun tomlSettings() = commonSettings().toml
}
