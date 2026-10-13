/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.toml.lang

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.CharsetToolkit
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.ParsingTestFixture
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.parsingFixture
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.TestFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import org.junit.jupiter.api.Test
import org.toml.getTomlTestsResourcesPath
import org.toml.lang.parse.TomlParserDefinition
import java.io.File


@TestApplication
class TomlParserTest {
    companion object {
        private val projectFixture: TestFixture<Project> = projectFixture()
    }

    private val parsingFixture = parsingFixture(
        projectFixture,
        getTomlTestsResourcesPath().toString() + "/org/toml/lang/parse/fixtures",
        "toml",
        true /*lowerCaseFirstLetter*/,
        TomlParserDefinition(),
    )

    @Test
    fun testEmpty() = doTest()
    @Test
    fun testKeys() = doTest()
    @Test
    fun testStrings() = doTest()
    @Test
    fun testNumbers() = doTest()
    @Test
    fun testBoolean() = doTest()
    @Test
    fun testTime() = doTest()
    @Test
    fun testArrays() = doTest()
    @Test
    fun testTables() = doTest()
    @Test
    fun testInlineTables() = doTest()
    @Test
    fun testArrayTables() = doTest()
    @Test
    fun testInvalid() = doTest()
    @Test
    fun testInitialWhitespace() = doTest()

    // The same as `loadFile()` of the fixture but trim loaded text only at the end.
    // Allows checking parsing with white spaces at the beginning of a file
    private fun loadFile(parsing: ParsingTestFixture, name: String): String {
        return FileUtil.loadFile(File(parsing.fullDataPath, name), CharsetToolkit.UTF8, true).trimEnd()
    }

    private fun doTest() {
        val parsing = parsingFixture.get()
        val name = parsing.testName
        val text = loadFile(parsing, "$name.${parsing.fileExt}")
        val file = parsing.parseFile(name, text)
        parsing.checkResult(name, file)
    }
}
