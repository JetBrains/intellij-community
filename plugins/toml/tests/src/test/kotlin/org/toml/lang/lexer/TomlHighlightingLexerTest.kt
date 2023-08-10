/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.toml.lang.lexer

import com.intellij.lexer.Lexer

class TomlHighlightingLexerTest : TomlLexerTestBase() {
    override fun getTestDataPath(): String = "org/toml/lang/lexer/fixtures/highlighting"

    override fun createLexer(): Lexer = TomlHighlightingLexer()

    fun `test basic string literals`() = doTest()
    fun `test literal string literals`() = doTest()
}
