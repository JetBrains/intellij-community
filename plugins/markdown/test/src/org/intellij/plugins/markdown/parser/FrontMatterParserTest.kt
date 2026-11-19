package org.intellij.plugins.markdown.parser

import com.intellij.idea.TestFor
import com.intellij.openapi.application.readAction
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.CharsetToolkit
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.codeInsightModuleFixture
import com.intellij.psi.impl.DebugUtil
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.TestDataPath
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInfo
import java.io.File

@TestApplication
@TestDataPath($$"$PROJECT_ROOT/community/plugins/markdown/test/data/parser/frontmatter")
class FrontMatterParserTest {
  companion object {
    private val projectFixture = projectFixture(openAfterCreation = true)
  }

  private val myFixture by codeInsightModuleFixture(projectFixture)

  private lateinit var testName: String

  @BeforeEach
  fun setUp(testInfo: TestInfo) {
    testName = PlatformTestUtil.getTestName(testInfo.testMethod.get().name, true).trimStart().replace(' ', '_')
  }

  @Test
  fun `test yaml header`(): Unit = doTest()

  @Test
  @TestFor(issues = ["IDEA-315838"])
  fun `test yaml header with dots as closing delimiter`(): Unit = doTest()

  @Test
  @TestFor(issues = ["IJPL-261379"])
  fun `test yaml header with trailing whitespace`(): Unit = doTest()

  @Test
  fun `dots are not recognised as opening delimiter`(): Unit = doTest()

  @Test
  fun `dots are not recognised as closing delimiter for toml`(): Unit = doTest()

  @Test
  fun `test toml header`(): Unit = doTest()

  @Test
  fun `yaml delimiters should not be paired with toml`(): Unit = doTest()

  @Test
  fun `unclosed yaml delimiter is treated as horizontal rule`(): Unit = doTest()

  private fun doTest(): Unit = timeoutRunBlocking {
    myFixture.configureByFile("$testName.md")
    val psi = readAction { DebugUtil.psiToString(myFixture.file, true, false) }
    val expectedContentPath = File(myFixture.testDataPath, "$testName.txt")
    val expected = FileUtil.loadFile(expectedContentPath, CharsetToolkit.UTF8, true)
    assertEquals(expected, psi)
  }
}
