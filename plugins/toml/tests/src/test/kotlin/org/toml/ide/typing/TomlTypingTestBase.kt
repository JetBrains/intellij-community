/*
* Use of this source code is governed by the MIT license that can be
* found in the LICENSE file.
*/

package org.toml.ide.typing

import org.intellij.lang.annotations.Language
import org.toml.TomlTestBase

abstract class TomlTypingTestBase : TomlTestBase() {
    protected fun doTestByText(@Language("TOML") before: String, @Language("TOML") after: String, c: Char = '\n') =
        checkByText(before.trimIndent(), after.trimIndent()) {
            myFixture.type(c)
        }
}
