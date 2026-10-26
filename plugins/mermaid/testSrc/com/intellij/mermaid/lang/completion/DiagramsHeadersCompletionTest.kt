package com.intellij.mermaid.lang.completion

import com.intellij.mermaid.lang.mermaidFixture
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import org.junit.jupiter.api.Test

@TestApplication
class DiagramsHeadersCompletionTest {
  companion object {
    private val projectFixture = projectFixture(openAfterCreation = true)
  }

  private val mermaid by mermaidFixture(projectFixture, "completion/diagrams")

  val diagrams = arrayOf(
    "pie",
    "journey",
    "flowchart",
    "sequenceDiagram",
    "classDiagram",
    "stateDiagram",
    "stateDiagram-v2",
    "erDiagram",
    "gantt",
    "requirementDiagram",
    "gitGraph",
    "C4Context",
    "C4Container",
    "C4Component",
    "C4Dynamic",
    "C4Deployment",
    "mindmap",
    "quadrantChart",
    "timeline",
    "zenuml",
    // mermaid 11.10.0 dropped the "-beta" suffix; both spellings are offered.
    "sankey",
    "sankey-beta",
    "xychart",
    "xychart-beta",
    "block",
    "block-beta",
    // Rendered by the bundled mermaid, parsed via the generic fallback rather than a detailed grammar.
    "architecture-beta",
    "cynefin-beta",
    "eventmodeling",
    "ishikawa-beta",
    "kanban",
    "packet",
    "radar-beta",
    "railroad-beta",
    "railroad-abnf-beta",
    "railroad-ebnf-beta",
    "railroad-peg-beta",
    "swimlane-beta",
    "treemap-beta",
    "treeView-beta",
    "venn-beta",
    "wardley-beta",
  )

  @Test
  fun `test diagrams headers`(): Unit = doTest(*diagrams)

  @Test
  fun `test diagrams headers after frontmatter`(): Unit = doTest(*diagrams)

  private fun doTest(vararg variants: String) {
    val testName = mermaid.getTestName(true)
    mermaid.fixture.testCompletionVariants("${testName}.mermaid", *variants)
  }
}
