package org.intellij.plugins.markdown.parser

import com.intellij.openapi.util.TextRange
import com.intellij.psi.SyntaxTraverser
import com.intellij.testFramework.LightPlatformCodeInsightTestCase
import com.intellij.testFramework.PerformanceUnitTest
import com.intellij.tools.ide.metrics.benchmark.Benchmark
import org.intellij.markdown.MarkdownElementTypes
import org.intellij.markdown.MarkdownTokenTypes as MarkdownLibraryTokenTypes
import org.intellij.markdown.ast.ASTNode
import org.intellij.plugins.markdown.lang.MarkdownTokenTypes
import org.intellij.plugins.markdown.injection.MarkdownCodeFenceUtils
import org.intellij.plugins.markdown.lang.parser.MarkdownParserManager
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownCodeFence
import org.junit.jupiter.api.assertDoesNotThrow

class MarkdownCodeFenceFlavourParserTest: LightPlatformCodeInsightTestCase() {
  fun `test valid mermaid fence`() {
    val content = """
    ::: mermaid
    :::
    """.trimIndent()
    configureFromFileText("some.md", content)
    val fence = findFence()
    assertNotNull("Failed to find a fence", fence)
    checkNotNull(fence)
    val language = fence.fenceLanguage?.trim()
    assertEquals("mermaid", language)
  }

  fun `test valid indented mermaid fence`() {
    val content = "    ::: mermaid\n    graph TD\n    :::"
    configureFromFileText("some.md", content)
    val fence = findFence()
    assertNotNull("Failed to find an indented fence", fence)
    checkNotNull(fence)
    assertEquals("mermaid", fence.fenceLanguage?.trim())
  }

  fun `test indented fence preserves content indentation`() {
    val content = "    ```python\n    if condition:\n        nested()\n    ```"
    configureFromFileText("some.md", content)
    val fence = findFence()
    assertNotNull("Failed to find an indented fence", fence)
    checkNotNull(fence)

    val contentLines = fence.children
      .filter { it.node.elementType == MarkdownTokenTypes.CODE_FENCE_CONTENT }
      .map { it.text.trimEnd('\r', '\n') }
    assertEquals(listOf("    if condition:", "        nested()"), contentLines)

    val decodedContent = StringBuilder()
    fence.createLiteralTextEscaper().decode(TextRange(0, fence.textLength), decodedContent)
    assertEquals("if condition:\n    nested()", decodedContent.toString())
  }

  fun `test unfinished indented fence with trailing newline does not throw`() {
    assertDoesNotThrow {
      configureFromFileText("some.md", "    ```python\n")
    }
  }

  fun `test CRLF indented fence captures language without carriage return`() {
    val content = "    ```java\r\n    class C {}\r\n    ```"
    val tree = MarkdownParserManager.parseContent(content)
    val languageNode = findNode(tree) { it.type == MarkdownLibraryTokenTypes.FENCE_LANG }
    val language = languageNode?.let { content.substring(it.startOffset, it.endOffset) }
    assertEquals("java", language)
  }

  fun `test indented closing line is one end token`() {
    val content = "    ```java\n    class C {}\n    ```   "
    val tree = MarkdownParserManager.parseContent(content)
    val endNode = checkNotNull(findNode(tree) { it.type == MarkdownLibraryTokenTypes.CODE_FENCE_END })
    assertEquals("    ```   ", content.substring(endNode.startOffset, endNode.endOffset))
  }

  fun `test delimiter syntax with non mermaid info string`() {
    val content = """
    ::: java
    :::
    """.trimIndent()
    configureFromFileText("some.md", content)
    val fence = findFence()
    assertNull("This fragment should not be parsed as a fence", fence)
  }

  fun `test indented fence with tab indentation`() {
    val content = "\t```python\n\tif condition:\n\t    nested()\n\t```"
    configureFromFileText("some.md", content)
    val fence = findFence()
    assertNotNull("Failed to find tab-indented fence", fence)
    checkNotNull(fence)
    val contentLines = fence.children
      .filter { it.node.elementType == MarkdownTokenTypes.CODE_FENCE_CONTENT }
      .map { it.text.trimEnd('\r', '\n') }
    assertEquals(listOf("\tif condition:", "\t    nested()"), contentLines)
  }

  fun `test indented fence with mixed space and tab indentation`() {
    val content = "  \t```python\n  \tif condition:\n  \t    nested()\n  \t```"
    configureFromFileText("some.md", content)
    val fence = findFence()
    assertNotNull("Failed to find mixed-indent fence", fence)
    checkNotNull(fence)
  }

  fun `test indented fence in list`() {
    val content = "- item\n\n      ```python\n      code\n      ```"
    configureFromFileText("some.md", content)
    val fence = findFence()
    assertNotNull("Failed to find fence in list", fence)
    checkNotNull(fence)
  }

  fun `test indented fence in block quote`() {
    val content = "> [^note]: First\n>\n>     ```python\n>     code\n>     ```"
    configureFromFileText("some.md", content)
    val fence = findFence()
    assertNotNull("Failed to find fence in block quote", fence)
    checkNotNull(fence)
  }

  fun `test unfinished indented fence does not consume next paragraph`() {
    val content = "    ```python\n    code\n\nNext paragraph"
    val tree = MarkdownParserManager.parseContent(content)
    val paragraphs = tree.children.filter { it.type == MarkdownElementTypes.PARAGRAPH }
    assertEquals("Should have exactly one paragraph after unfinished fence", 1, paragraphs.size)
  }

  fun `test unfinished indented fence in nested containers does not throw`() {
    assertDoesNotThrow {
      MarkdownParserManager.parseContent("- item\n\n      ```python\n      code")
      MarkdownParserManager.parseContent(">\n>     ```python\n>     code")
    }
  }

  fun `test CRLF blank line in indented fence`() {
    val content = "    ```python\r\n    code\r\n\r\n    more\r\n    ```"
    val tree = MarkdownParserManager.parseContent(content)
    val fence = findNode(tree) { it.type == MarkdownElementTypes.CODE_FENCE }
    assertNotNull("Failed to find fence with CRLF blank line", fence)
  }

  fun `test indentation info`() {
    assertEquals(0, MarkdownCodeFenceUtils.getIndentationInfo("").columns)
    assertEquals(1, MarkdownCodeFenceUtils.getIndentationInfo(" ").columns)
    assertEquals(4, MarkdownCodeFenceUtils.getIndentationInfo("\t").columns)
    assertEquals(6, MarkdownCodeFenceUtils.getIndentationInfo("\t  ").columns)

    val indentation = MarkdownCodeFenceUtils.getIndentationInfo(" \t code", maxColumns = 4)
    assertEquals(2, indentation.length)
    assertEquals(4, indentation.columns)
  }

  @PerformanceUnitTest
  fun `test long invalid fence does not cause regex backtracking`() {
    val content = "~".repeat(20_000) + "`"
    Benchmark.newBenchmark("highlighting") {
      configureFromFileText("some.md", content)
    }.start()
  }

  private fun findFence(): MarkdownCodeFence? {
    val elements = SyntaxTraverser.psiTraverser(file).asSequence()
    return elements.filterIsInstance<MarkdownCodeFence>().firstOrNull()
  }

  private fun findNode(node: ASTNode, predicate: (ASTNode) -> Boolean): ASTNode? {
    if (predicate(node)) {
      return node
    }
    return node.children.firstNotNullOfOrNull { findNode(it, predicate) }
  }
}
