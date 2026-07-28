package org.intellij.plugins.markdown.parser

import com.intellij.psi.SyntaxTraverser
import com.intellij.testFramework.LightPlatformCodeInsightTestCase
import com.intellij.testFramework.PerformanceUnitTest
import com.intellij.tools.ide.metrics.benchmark.Benchmark
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownCodeFence

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

  fun `test delimiter syntax with non mermaid info string`() {
    val content = """
    ::: java
    :::
    """.trimIndent()
    configureFromFileText("some.md", content)
    val fence = findFence()
    assertNull("This fragment should not be parsed as a fence", fence)
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
}
