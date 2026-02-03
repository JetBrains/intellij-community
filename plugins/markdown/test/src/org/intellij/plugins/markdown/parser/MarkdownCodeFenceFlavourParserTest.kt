package org.intellij.plugins.markdown.parser

import com.intellij.psi.SyntaxTraverser
import com.intellij.testFramework.LightPlatformCodeInsightTestCase
import junit.framework.TestCase
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownCodeFence

class MarkdownCodeFenceFlavourParserTest: LightPlatformCodeInsightTestCase() {
  fun `test valid mermaid fence`() {
    val content = """
    ::: mermaid
    :::
    """.trimIndent()
    configureFromFileText("some.md", content)
    val fence = findFence()
    TestCase.assertNotNull("Failed to find a fence", fence)
    checkNotNull(fence)
    val language = fence.fenceLanguage?.trim()
    TestCase.assertEquals("mermaid", language)
  }

  fun `test delimiter syntax with non mermaid info string`() {
    val content = """
    ::: java
    :::
    """.trimIndent()
    configureFromFileText("some.md", content)
    val fence = findFence()
    TestCase.assertNull("This fragment should not be parsed as a fence", fence)
  }

  private fun findFence(): MarkdownCodeFence? {
    val elements = SyntaxTraverser.psiTraverser(file).asSequence()
    return elements.filterIsInstance<MarkdownCodeFence>().firstOrNull()
  }
}
