/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.toml.lang.lexer

import com.intellij.lexer.Lexer
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.CharsetToolkit
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.lexerFixture
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.junit5.fixture.TestFixtures
import org.junit.jupiter.api.fail
import org.toml.TestCase
import org.toml.getTomlTestsResourcesPath
import org.toml.pathToGoldTestFile
import org.toml.pathToSourceTestFile
import java.io.IOException

@TestFixtures
abstract class LexerTestCaseBase : TestCase {
    private val lexer by lexerFixture(getTomlTestsResourcesPath().toString()) { createLexer() }

    protected abstract fun createLexer(): Lexer

    override fun getTestName(lowercaseFirstLetter: Boolean): String = TestCase.camelOrWordsToSnake(lexer.testName)

    // NOTE(matkad): this is basically a copy-paste of doFileTest.
    // The only difference is that encoding is set to utf-8
    protected fun doTest() {
        val filePath = pathToSourceTestFile()
        val text = try {
            val fileText = FileUtil.loadFile(filePath.toFile(), CharsetToolkit.UTF8)
            StringUtil.convertLineSeparators(fileText.trim())
        } catch (e: IOException) {
            fail("can't load file " + filePath + ": " + e.message)
        }
        PlatformTestUtil.assertSameLinesWithFile(pathToGoldTestFile().toFile().canonicalPath, lexer.printTokens(text, 0))
        lexer.checkCorrectRestart(text)
    }
}
