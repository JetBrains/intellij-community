/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.toml.ide.todo

import com.intellij.editor.TodoItemsTestUtil
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.codeInsightModuleFixture
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import org.junit.jupiter.api.Test
import org.toml.lang.psi.TomlFileType

@TestApplication
class TomlTodoTest {
    companion object {
        private val projectFixture = projectFixture(openAfterCreation = true)
    }

    private val myFixture by codeInsightModuleFixture(projectFixture)

    private fun testTodos(text: String) = TodoItemsTestUtil.testTodos(myFixture, TomlFileType.defaultExtension, text)

    @Test
    fun `test single todo`(): Unit = testTodos("""
        # [TODO first line]
        # second line
    """)

    @Test
    fun `test multiline todo`(): Unit = testTodos("""
        # [TODO first line]
        #  [second line]
    """)

    // TodoItemsTestCase skipped the tests below for this language. They keep its test names.
    @Test
    fun testSuccessiveLineComments() {
    }

    @Test
    fun testSuccessiveLineCommentsAfterEditing() {
    }

    @Test
    fun testNewTodoAfterUnsavedEditing() {
    }

    @Test
    fun testAllLinesLoseHighlightingWithFirstLine() {
    }

    @Test
    fun testContinuationIsNotOverlappedWithFollowingTodo() {
    }

    @Test
    fun testContinuationInBlockCommentWithStars() {
    }
}
