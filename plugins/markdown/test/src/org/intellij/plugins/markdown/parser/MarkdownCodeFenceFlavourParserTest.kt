package org.intellij.plugins.markdown.parser

import com.intellij.openapi.application.readAction
import com.intellij.openapi.util.TextRange
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.codeInsightModuleFixture
import com.intellij.psi.SyntaxTraverser
import com.intellij.testFramework.PerformanceUnitTest
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.tools.ide.metrics.benchmark.Benchmark
import org.intellij.markdown.MarkdownElementTypes
import org.intellij.markdown.MarkdownTokenTypes as MarkdownLibraryTokenTypes
import org.intellij.markdown.ast.ASTNode
import org.intellij.plugins.markdown.lang.MarkdownTokenTypes
import org.intellij.plugins.markdown.injection.MarkdownCodeFenceUtils
import org.intellij.plugins.markdown.lang.parser.MarkdownParserManager
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownCodeFence
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow

@TestApplication
class MarkdownCodeFenceFlavourParserTest {
  companion object {
    private val projectFixture = projectFixture(openAfterCreation = true)
  }

  private val myFixture by codeInsightModuleFixture(projectFixture)

  @Test
  fun `test valid mermaid fence`(): Unit = timeoutRunBlocking {
    val content = """
    ::: mermaid
    :::
    """.trimIndent()
    myFixture.configureByText("some.md", content)
    readAction {
      val fence = findFence()
      assertNotNull(fence, "Failed to find a fence")
      checkNotNull(fence)
      val language = fence.fenceLanguage?.trim()
      assertEquals("mermaid", language)
    }
  }

  @Test
  fun `test valid indented mermaid fence`(): Unit = timeoutRunBlocking {
    val content = "    ::: mermaid\n    graph TD\n    :::"
    myFixture.configureByText("some.md", content)
    readAction {
      val fence = findFence()
      assertNotNull(fence, "Failed to find an indented fence")
      checkNotNull(fence)
      assertEquals("mermaid", fence.fenceLanguage?.trim())
    }
  }

  @Test
  fun `test indented fence preserves content indentation`(): Unit = timeoutRunBlocking {
    val content = "    ```python\n    if condition:\n        nested()\n    ```"
    myFixture.configureByText("some.md", content)
    readAction {
      val fence = findFence()
      assertNotNull(fence, "Failed to find an indented fence")
      checkNotNull(fence)

      val contentLines = fence.children
        .filter { it.node.elementType == MarkdownTokenTypes.CODE_FENCE_CONTENT }
        .map { it.text.trimEnd('\r', '\n') }
      assertEquals(listOf("    if condition:", "        nested()"), contentLines)

      val decodedContent = StringBuilder()
      fence.createLiteralTextEscaper().decode(TextRange(0, fence.textLength), decodedContent)
      assertEquals("if condition:\n    nested()", decodedContent.toString())
    }
  }

  @Test
  fun `test unfinished indented fence with trailing newline does not throw`() {
    assertDoesNotThrow {
      myFixture.configureByText("some.md", "    ```python\n")
    }
  }

  @Test
  fun `test CRLF indented fence captures language without carriage return`() {
    val content = "    ```java\r\n    class C {}\r\n    ```"
    val tree = MarkdownParserManager.parseContent(content)
    val languageNode = findNode(tree) { it.type == MarkdownLibraryTokenTypes.FENCE_LANG }
    val language = languageNode?.let { content.substring(it.startOffset, it.endOffset) }
    assertEquals("java", language)
  }

  @Test
  fun `test indented closing line is one end token`() {
    val content = "    ```java\n    class C {}\n    ```   "
    val tree = MarkdownParserManager.parseContent(content)
    val endNode = checkNotNull(findNode(tree) { it.type == MarkdownLibraryTokenTypes.CODE_FENCE_END })
    assertEquals("    ```   ", content.substring(endNode.startOffset, endNode.endOffset))
  }

  @Test
  fun `test delimiter syntax with non mermaid info string`(): Unit = timeoutRunBlocking {
    val content = """
    ::: java
    :::
    """.trimIndent()
    myFixture.configureByText("some.md", content)
    readAction {
      val fence = findFence()
      assertNull(fence, "This fragment should not be parsed as a fence")
    }
  }

  @Test
  fun `test indented fence with tab indentation`(): Unit = timeoutRunBlocking {
    val content = "\t```python\n\tif condition:\n\t    nested()\n\t```"
    myFixture.configureByText("some.md", content)
    readAction {
      val fence = findFence()
      assertNotNull(fence, "Failed to find tab-indented fence")
      checkNotNull(fence)
      val contentLines = fence.children
        .filter { it.node.elementType == MarkdownTokenTypes.CODE_FENCE_CONTENT }
        .map { it.text.trimEnd('\r', '\n') }
      assertEquals(listOf("\tif condition:", "\t    nested()"), contentLines)
    }
  }

  @Test
  fun `test indented fence with mixed space and tab indentation`(): Unit = timeoutRunBlocking {
    val content = "  \t```python\n  \tif condition:\n  \t    nested()\n  \t```"
    myFixture.configureByText("some.md", content)
    readAction {
      val fence = findFence()
      assertNotNull(fence, "Failed to find mixed-indent fence")
      checkNotNull(fence)
    }
  }

  @Test
  fun `test indented fence in list`(): Unit = timeoutRunBlocking {
    val content = "- item\n\n      ```python\n      code\n      ```"
    myFixture.configureByText("some.md", content)
    readAction {
      val fence = findFence()
      assertNotNull(fence, "Failed to find fence in list")
      checkNotNull(fence)
    }
  }

  @Test
  fun `test indented fence in block quote`(): Unit = timeoutRunBlocking {
    val content = "> [^note]: First\n>\n>     ```python\n>     code\n>     ```"
    myFixture.configureByText("some.md", content)
    readAction {
      val fence = findFence()
      assertNotNull(fence, "Failed to find fence in block quote")
      checkNotNull(fence)
    }
  }

  @Test
  fun `test unfinished indented fence does not consume next paragraph`() {
    val content = "    ```python\n    code\n\nNext paragraph"
    val tree = MarkdownParserManager.parseContent(content)
    val paragraphs = tree.children.filter { it.type == MarkdownElementTypes.PARAGRAPH }
    assertEquals(1, paragraphs.size, "Should have exactly one paragraph after unfinished fence")
  }

  @Test
  fun `test unfinished indented fence in nested containers does not throw`() {
    assertDoesNotThrow {
      MarkdownParserManager.parseContent("- item\n\n      ```python\n      code")
      MarkdownParserManager.parseContent(">\n>     ```python\n>     code")
    }
  }

  @Test
  fun `test CRLF blank line in indented fence`() {
    val content = "    ```python\r\n    code\r\n\r\n    more\r\n    ```"
    val tree = MarkdownParserManager.parseContent(content)
    val fence = findNode(tree) { it.type == MarkdownElementTypes.CODE_FENCE }
    assertNotNull(fence, "Failed to find fence with CRLF blank line")
  }

  @Test
  fun `test indentation info`() {
    assertEquals(0, MarkdownCodeFenceUtils.getIndentationInfo("").columns)
    assertEquals(1, MarkdownCodeFenceUtils.getIndentationInfo(" ").columns)
    assertEquals(4, MarkdownCodeFenceUtils.getIndentationInfo("\t").columns)
    assertEquals(6, MarkdownCodeFenceUtils.getIndentationInfo("\t  ").columns)

    val indentation = MarkdownCodeFenceUtils.getIndentationInfo(" \t code", maxColumns = 4)
    assertEquals(2, indentation.length)
    assertEquals(4, indentation.columns)
  }

  @Test
  @PerformanceUnitTest
  fun `test long invalid fence does not cause regex backtracking`() {
    val content = "~".repeat(20_000) + "`"
    Benchmark.newBenchmark("highlighting") {
      myFixture.configureByText("some.md", content)
    }.runAsStressTest().start()
  }

  private fun findFence(): MarkdownCodeFence? {
    val elements = SyntaxTraverser.psiTraverser(myFixture.file).asSequence()
    return elements.filterIsInstance<MarkdownCodeFence>().firstOrNull()
  }

  private fun findNode(node: ASTNode, predicate: (ASTNode) -> Boolean): ASTNode? {
    if (predicate(node)) {
      return node
    }
    return node.children.firstNotNullOfOrNull { findNode(it, predicate) }
  }
}
