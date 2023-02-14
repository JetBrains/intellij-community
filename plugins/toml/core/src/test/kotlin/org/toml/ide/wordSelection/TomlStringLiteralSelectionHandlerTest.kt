/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.toml.ide.wordSelection

class TomlStringLiteralSelectionHandlerTest : TomlSelectionHandlerTestBase() {

    fun `test select whole string literal value`() = doTest("""
        serde = "1<caret>.0.104"
    """, """
        serde = "<selection>1</selection>.0.104"
    """, """
        serde = "<selection>1.0.104</selection>"
    """, """
        serde = <selection>"1.0.104"</selection>
    """)

    fun `test select string literal escape symbols`() = doTest("""
        key = "foo <caret>\u002D bar"
    """, """
        key = "foo <selection><caret>\u002D</selection> bar"
    """)
}
