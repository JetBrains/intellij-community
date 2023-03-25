/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.toml.ide.wordSelection

import com.intellij.codeInsight.editorActions.SelectWordHandler
import com.intellij.ide.DataManager
import org.intellij.lang.annotations.Language
import org.toml.TomlTestBase

abstract class TomlSelectionHandlerTestBase : TomlTestBase() {

    protected fun doTest(@Language("TOML") before: String, @Language("TOML") vararg after: String) {
        InlineFile(before)

        val action = SelectWordHandler(null)
        val dataContext = DataManager.getInstance().getDataContext(myFixture.editor.component)
        for (text in after) {
            action.execute(myFixture.editor, null, dataContext)
            myFixture.checkResult(text.trimIndent(), false)
        }
    }
}
