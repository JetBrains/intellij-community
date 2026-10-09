/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.toml.lang.lexer

import com.intellij.platform.testFramework.junit5.codeInsight.fixture.lexerFixture
import com.intellij.testFramework.junit5.fixture.TestFixtures
import org.junit.jupiter.api.Test
import org.toml.getTomlTestsResourcesPath
import org.toml.lang.psi.TomlElementTypes.BASIC_STRING

@TestFixtures
class TomlEscapeLexerTest {
    private val lexer by lexerFixture(getTomlTestsResourcesPath().resolve("org/toml/lang/lexer/fixtures/escapes").toString()) {
        TomlEscapeLexer.of(BASIC_STRING)
    }

    @Test
    fun `test valid symbol escapes`() = lexer.doFileTest("toml")
    @Test
    fun `test valid unicode escapes`() = lexer.doFileTest("toml")
    @Test
    fun `test invalid symbol escapes`() = lexer.doFileTest("toml")
    @Test
    fun `test invalid unicode escapes`() = lexer.doFileTest("toml")
}
