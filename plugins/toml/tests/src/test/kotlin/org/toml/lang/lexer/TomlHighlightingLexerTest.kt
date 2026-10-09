/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.toml.lang.lexer

import com.intellij.platform.testFramework.junit5.codeInsight.fixture.lexerFixture
import com.intellij.testFramework.junit5.fixture.TestFixtures
import org.junit.jupiter.api.Test
import org.toml.getTomlTestsResourcesPath

@TestFixtures
class TomlHighlightingLexerTest {
    private val lexer by lexerFixture(getTomlTestsResourcesPath().resolve("org/toml/lang/lexer/fixtures/highlighting").toString()) {
        TomlHighlightingLexer()
    }

    @Test
    fun `test basic string literals`() = lexer.doFileTest("toml")
    @Test
    fun `test literal string literals`() = lexer.doFileTest("toml")
}
