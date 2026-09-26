/*
* Use of this source code is governed by the MIT license that can be
* found in the LICENSE file.
*/

package org.toml.ide.formatter

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.psi.codeStyle.CodeStyleManager
import org.intellij.lang.annotations.Language
import org.toml.TomlTestBase

abstract class TomlFormatterTestBase : TomlTestBase() {
    protected fun doTest(@Language("TOML") before: String, @Language("TOML") after: String) {
        checkByText(before.trimIndent(), after.trimIndent()) {
            WriteCommandAction.runWriteCommandAction(project) {
                val file = myFixture.file
                CodeStyleManager.getInstance(project)
                    .reformatText(file, file.textRange.startOffset, file.textRange.endOffset)
            }
        }
    }
}
