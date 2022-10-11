package com.intellij.mermaid.lang.symbol

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.util.io.readText
import junit.framework.TestCase
import kotlin.io.path.Path

class IdentifierFindUsagesTest : BasePlatformTestCase() {
  fun `test find usages of identifier without declaration`() = doTest()

  fun `test find usages of identifier reference with one declaration`() = doTest()

  fun `test find usages of unique declaration with references`() = doTest()

  fun `test find usages of identifier reference with several declarations`() = doTest()

  fun `test find usages of several declarations`() = doTest()

  private fun doTest() {
    val testName = getTestName(true)
    val contentFile = "$testName.mermaid"
    val expectedFile = "${testName}.txt"
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
    return "src/test/resources/symbol/usages"
  }
}
