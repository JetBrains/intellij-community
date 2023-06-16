/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.toml.lang.lexer

import com.intellij.lexer.Lexer
import org.toml.lang.psi.TomlElementTypes.BASIC_STRING

class TomlEscapeLexerTest : TomlLexerTestBase() {
    override fun getTestDataPath(): String = "org/toml/lang/lexer/fixtures/escapes"

    override fun createLexer(): Lexer = TomlEscapeLexer.of(BASIC_STRING)

    fun `test valid symbol escapes`() = doTest()
    fun `test valid unicode escapes`() = doTest()
    fun `test invalid symbol escapes`() = doTest()
    fun `test invalid unicode escapes`() = doTest()
}
