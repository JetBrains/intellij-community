package org.intellij.plugins.markdown.model

import com.intellij.platform.testFramework.junit5.codeInsight.fixture.codeInsightModuleFixture
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.TestDataPath
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInfo
import kotlin.io.path.Path
import kotlin.io.path.readText

@TestApplication
@TestDataPath($$"$PROJECT_ROOT/community/plugins/markdown/test/data/model/headers/usages/file")
class SingleFileHeaderFindUsagesTest {
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
  fun `find usages`(): Unit = doTest()

  private fun doTest() {
    val contentFile = "$testName.md"
    val expectedFile = "${testName}_expected.txt"
    val usages = myFixture.testFindUsagesUsingAction(contentFile).map { it.toString() }.sorted()
    val text = usages.joinToString(separator = "\n", postfix = "\n")
    val expectedFilePath = "${myFixture.testDataPath}/$expectedFile"
    val expected = Path(expectedFilePath).readText()
    assertEquals(expected, text)
  }
}
