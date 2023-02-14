/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.toml.lang

import com.intellij.lang.LanguageASTFactory
import com.intellij.lang.LanguageBraceMatching
import com.intellij.testFramework.ParsingTestCase
import org.toml.getTomlTestsResourcesPath
import org.toml.ide.TomlBraceMatcher
import org.toml.lang.parse.TomlParserDefinition
import org.toml.lang.psi.impl.TomlASTFactory


class TomlParserTest
    : ParsingTestCase("org/toml/lang/parse/fixtures", "toml", true /*lowerCaseFirstLetter*/, TomlParserDefinition()) {

    fun testEmpty() = doTest()
    fun testKeys() = doTest()
    fun testStrings() = doTest()
    fun testNumbers() = doTest()
    fun testBoolean() = doTest()
    fun testTime() = doTest()
    fun testArrays() = doTest()
    fun testTables() = doTest()
    fun testInlineTables() = doTest()
    fun testArrayTables() = doTest()
    fun testInvalid() = doTest()

    override fun getTestDataPath() = getTomlTestsResourcesPath().toString()
    override fun setUp() {
        super.setUp()
        addExplicitExtension(LanguageASTFactory.INSTANCE, myLanguage, TomlASTFactory())
        addExplicitExtension(LanguageBraceMatching.INSTANCE, myLanguage, TomlBraceMatcher())
    }

    private fun doTest() = doTest(true)
}
