/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.toml.ide.wordSelection

import com.intellij.codeInsight.editorActions.SelectWordHandler
import com.intellij.ide.DataManager
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.intellij.lang.annotations.Language

abstract class TomlSelectionHandlerTestBase : BasePlatformTestCase() {

    protected fun doTest(@Language("TOML") before: String, @Language("TOML") vararg after: String) {
        myFixture.configureByText("example.toml", before)
        val action = SelectWordHandler(null)
        val dataContext = DataManager.getInstance().getDataContext(myFixture.editor.component)
        for (text in after) {
            action.execute(myFixture.editor, null, dataContext)
            myFixture.checkResult(text, false)
        }
    }
}
