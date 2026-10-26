package com.intellij.mermaid.lang.symbol

import com.intellij.mermaid.lang.mermaidFixture
import com.intellij.mermaid.lang.psi.symbol.MermaidSymbol
import com.intellij.mermaid.lang.psi.symbol.identifier.IdentifierSymbolReferenceProvider
import com.intellij.model.psi.PsiExternalReferenceHost
import com.intellij.model.psi.PsiSymbolReference
import com.intellij.model.psi.PsiSymbolReferenceHints
import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.psi.util.elementsAtOffsetUp
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import kotlin.io.path.Path
import kotlin.io.path.readText

@Suppress("UnstableApiUsage")
@TestApplication
class IdentifierResolveTest {
  companion object {
    private val projectFixture = projectFixture(openAfterCreation = true)
  }

  private val mermaid by mermaidFixture(projectFixture, "symbol/resolve")

  @Test
  fun `test resolve identifier without declaration`(): Unit = doTest()

  @Test
  fun `test resolve identifier reference with one declaration`(): Unit = doTest()

  @Test
  fun `test resolve unique declaration with references`(): Unit = doTest()

  @Test
  fun `test resolve identifier reference with several declarations`(): Unit = doTest()

  @Test
  fun `test resolve several declarations`(): Unit = doTest()

  private fun doTest() {
    val testName = mermaid.getTestName(true)
    val contentFile = "$testName.mermaid"
    val expectedFile = "${testName}.txt"
    mermaid.fixture.configureByFile(contentFile)
    val rangeList = runReadActionBlocking {
      val references = findReferencesAtCaret()
      val resolved = references.flatMap { it.resolveReference().filterIsInstance<MermaidSymbol>() }
      resolved.sortedBy { it.range.startOffset }.map { "${it.range} |${it.searchText}|" }
    }
    val text = rangeList.joinToString(separator = "\n", postfix = "\n")
    val expectedFilePath = "${mermaid.testDataPath}/$expectedFile"
    val expected = Path(expectedFilePath).readText()
    assertEquals(expected, text)
  }

  private fun findReferencesAtCaret(): Collection<PsiSymbolReference> {
    val referenceProvider = IdentifierSymbolReferenceProvider()
    val elements = mermaid.fixture.file.elementsAtOffsetUp(mermaid.fixture.caretOffset)
    for ((element, offset) in elements) {
      if (element is PsiExternalReferenceHost) {
        val references = referenceProvider.getReferences(element, PsiSymbolReferenceHints.offsetHint(offset))
        if (references.isNotEmpty()) {
          return references
        }
      }
    }
    return emptyList()
  }
}
