/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.toml.ide.todo

import com.intellij.editor.TodoItemsTestCase
import org.toml.lang.psi.TomlFileType

class TomlTodoTest : TodoItemsTestCase() {
    override fun getFileExtension(): String = TomlFileType.defaultExtension
    override fun supportsCStyleMultiLineComments(): Boolean = false
    override fun supportsCStyleSingleLineComments(): Boolean = false

    fun `test single todo`() = testTodos("""
        # [TODO first line]
        # second line
    """)

    fun `test multiline todo`() = testTodos("""
        # [TODO first line]
        #  [second line]
    """)
}
