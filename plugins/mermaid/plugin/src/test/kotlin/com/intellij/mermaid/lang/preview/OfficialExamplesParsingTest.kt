package com.intellij.mermaid.lang.preview

import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.intellij.testFramework.junit5.RunInEdt
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.util.io.readText
import org.junit.jupiter.api.*
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.RegisterExtension
import java.nio.file.Path
import kotlin.io.path.name
import kotlin.io.path.nameWithoutExtension

@RunInEdt
@TestApplication
class OfficialExamplesParsingTest {
  @JvmField
  @RegisterExtension
  val fixtureExtension = CodeInsightFixtureExtension(OfficialExamplesParsingTest::class.simpleName!!)

  private val fixture: CodeInsightTestFixture
    get() = fixtureExtension.fixture

  private val ignoredTests = listOf(
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

  @TestTemplate
  @ExtendWith(OfficialDocumentationExamplesContext::class)
  fun testDiagram(path: Path) {
    Assumptions.assumeFalse { path.nameWithoutExtension in ignoredTests }
    fixture.configureByText(path.name, path.readText())
    fixture.checkHighlighting(true, true, true, false)
  }
}
