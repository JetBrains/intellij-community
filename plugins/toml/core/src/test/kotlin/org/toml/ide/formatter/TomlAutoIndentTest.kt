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
}
