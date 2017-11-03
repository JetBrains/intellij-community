/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.toml.lang

import com.intellij.testFramework.ParsingTestCase
import org.toml.lang.parse.TomlParserDefinition


class TomlParseValidTest
    : ParsingTestCase("org/toml/lang/parse/fixtures/valid", "toml", true /*lowerCaseFirstLetter*/, TomlParserDefinition()) {

    fun testEmpty() = doTest()
    fun testKeys() = doTest()
    fun testStrings() = doTest()
    fun testNumbers() = doTest()
    fun testBoolean() = doTest()
    fun testTime() = doTest()


    override fun getTestDataPath() = "src/test/resources"
    private fun doTest() = doTest(true)

}
