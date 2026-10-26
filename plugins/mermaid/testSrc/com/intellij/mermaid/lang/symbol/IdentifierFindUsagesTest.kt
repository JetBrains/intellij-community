package com.intellij.mermaid.lang.symbol

import com.intellij.mermaid.lang.mermaidFixture
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import kotlin.io.path.Path
import kotlin.io.path.readText

@TestApplication
class IdentifierFindUsagesTest {
  companion object {
    private val projectFixture = projectFixture(openAfterCreation = true)
  }

  private val mermaid by mermaidFixture(projectFixture, "symbol/usages")

  @Test
  fun `test find usages of identifier without declaration`(): Unit = doTest()

  @Test
  fun `test find usages of identifier reference with one declaration`(): Unit = doTest()

  @Test
  fun `test find usages of unique declaration with references`(): Unit = doTest()

  @Test
  fun `test find usages of identifier reference with several declarations`(): Unit = doTest()

  @Test
  fun `test find usages of several declarations`(): Unit = doTest()

  private fun doTest() {
    val testName = mermaid.getTestName(true)
    val contentFile = "$testName.mermaid"
    val expectedFile = "${testName}.txt"
    val usages = mermaid.fixture.testFindUsagesUsingAction(contentFile).map { it.toString() }.sorted()
    val text = usages.joinToString(separator = "\n", postfix = "\n")
    val expectedFilePath = "${mermaid.testDataPath}/$expectedFile"
    val expected = Path(expectedFilePath).readText()
    assertEquals(expected, text)
  }
}
