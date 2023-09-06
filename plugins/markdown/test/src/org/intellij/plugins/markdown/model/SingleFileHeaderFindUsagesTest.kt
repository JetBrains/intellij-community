package org.intellij.plugins.markdown.model

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import junit.framework.TestCase
import org.intellij.plugins.markdown.MarkdownTestingUtil
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import kotlin.io.path.Path
import kotlin.io.path.readText

@RunWith(JUnit4::class)
class SingleFileHeaderFindUsagesTest: BasePlatformTestCase() {
  @Test
  fun `find usages`() = doTest()

  private fun doTest() {
    val testName = getTestName(true)
    val contentFile = "$testName.md"
    val expectedFile = "${testName}_expected.txt"
    val usages = myFixture.testFindUsagesUsingAction(contentFile).map { it.toString() }.sorted()
    val text = usages.joinToString(separator = "\n", postfix = "\n")
    val expectedFilePath = "$testDataPath/$expectedFile"
    val expected = Path(expectedFilePath).readText()
    TestCase.assertEquals(expected, text)
  }

  override fun getTestName(lowercaseFirstLetter: Boolean): String {
    val name = super.getTestName(lowercaseFirstLetter)
    return name.trimStart().replace(' ', '_')
  }

  override fun getTestDataPath(): String {
    return "${MarkdownTestingUtil.TEST_DATA_PATH}/model/headers/usages/file"
  }
}
