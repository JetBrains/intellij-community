package org.intellij.plugins.markdown.parser

import com.intellij.idea.TestFor
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.CharsetToolkit
import com.intellij.psi.impl.DebugUtil
import com.intellij.testFramework.LightPlatformCodeInsightTestCase
import junit.framework.TestCase
import org.intellij.plugins.markdown.MarkdownTestingUtil
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import java.io.File

@RunWith(JUnit4::class)
class FrontMatterParserTest: LightPlatformCodeInsightTestCase() {
  @Test
  fun `test yaml header`() = doTest()

  @Test
  @TestFor(issues = ["IDEA-315838"])
  fun `test yaml header with dots as closing delimiter`() = doTest()

  @Test
  fun `dots are not recognised as opening delimiter`() = doTest()

  @Test
  fun `dots are not recognised as closing delimiter for toml`() = doTest()

  @Test
  fun `test toml header`() = doTest()

  @Test
  fun `yaml delimiters should not be paired with toml`() = doTest()

  private fun doTest() {
    val testName = getTestName(true)
    configureByFile("$testName.md")
    val psi = DebugUtil.psiToString(file, true, false)
    val expectedContentPath = File(testDataPath, "$testName.txt")
    val expected = FileUtil.loadFile(expectedContentPath, CharsetToolkit.UTF8, true)
    TestCase.assertEquals(expected, psi)
  }

  override fun getTestName(lowercaseFirstLetter: Boolean): String {
    val name = super.getTestName(lowercaseFirstLetter)
    return name.trimStart().replace(' ', '_')
  }

  override fun getTestDataPath(): String {
    return "${MarkdownTestingUtil.TEST_DATA_PATH}/parser/frontmatter/"
  }
}
