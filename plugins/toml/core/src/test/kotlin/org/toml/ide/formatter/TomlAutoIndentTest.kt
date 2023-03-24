/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.toml.ide.formatter

import org.toml.ide.typing.TomlTypingTestBase

class TomlAutoIndentTest : TomlTypingTestBase() {
  fun `test new array element`() = doTestByText("""
        [key]
        foo = [
            "text",<caret>
        ]
    """, """
        [key]
        foo = [
            "text",
            <caret>
        ]
    """)

  fun `test new key value inside table`() = doOptionTest(tomlSettings()::INDENT_TABLE_KEYS, """
        [foo]
        bar = 1

        [key]
            foo = 1<caret>
    """, """
        [foo]
        bar = 1

        [key]
            foo = 1
            <caret>
    """, """
        [foo]
        bar = 1

        [key]
            foo = 1
        <caret>
    """)
}
