package com.intellij.mermaid.lang.preview

import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.intellij.testFramework.junit5.RunInEdt
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.util.io.readText
import org.junit.jupiter.api.*
import org.junit.jupiter.api.extension.RegisterExtension
import java.nio.file.Path
import kotlin.io.path.name
import kotlin.io.path.nameWithoutExtension

@RunInEdt
@TestApplication
class OfficialExamplesParsingTest {
  @RegisterExtension
  @JvmField
  val fixtureExtension = CodeInsightFixtureExtension(OfficialExamplesParsingTest::class.simpleName!!)

  private val fixture: CodeInsightTestFixture
    get() = fixtureExtension.fixture

  @TestFactory
  fun generateExampleTests(): List<DynamicNode> {
    OfficialExamplesTestData.assumeAvailable()
    val ignoredTests = listOf(
      "flowchart-14",
      "flowchart-15",
      "flowchart-16",
      "flowchart-17",
      "gitgraph-14",
      "sequenceDiagram-0",
      "sequenceDiagram-21",
      "examples-2",
      "examples-7"
    )
    return OfficialExamplesTestData.generateDynamicTests(
      testDataPath = OfficialExamplesTestData.testDataPath,
      acceptExampleCondition = { it.nameWithoutExtension !in ignoredTests },
      test = ::testDiagram
    )
  }

  private fun testDiagram(path: Path) {
    fixture.configureByText(path.name, path.readText())
    fixture.checkHighlighting(true, true, true, false)
  }
}
