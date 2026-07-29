package com.intellij.mermaid.lang.completion

import com.intellij.mermaid.lang.MermaidBaseTestCase

class DiagramsHeadersCompletionTest : MermaidBaseTestCase("completion/diagrams") {
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

  fun `test diagrams headers`() = doTest(*diagrams)

  fun `test diagrams headers after frontmatter`() = doTest(*diagrams)

  private fun doTest(vararg variants: String) {
    val testName = getTestName(true)
    myFixture.testCompletionVariants("${testName}.mermaid", *variants)
  }
}
