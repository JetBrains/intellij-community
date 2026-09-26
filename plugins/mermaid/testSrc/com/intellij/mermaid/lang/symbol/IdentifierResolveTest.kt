package com.intellij.mermaid.lang.symbol

import com.intellij.mermaid.lang.MermaidBaseTestCase
import com.intellij.mermaid.lang.psi.symbol.MermaidSymbol
import com.intellij.mermaid.lang.psi.symbol.identifier.IdentifierSymbolReferenceProvider
import com.intellij.model.psi.PsiExternalReferenceHost
import com.intellij.model.psi.PsiSymbolReference
import com.intellij.model.psi.PsiSymbolReferenceHints
import com.intellij.psi.util.elementsAtOffsetUp
import junit.framework.TestCase
import kotlin.io.path.Path
import kotlin.io.path.readText

@Suppress("UnstableApiUsage")
class IdentifierResolveTest : MermaidBaseTestCase("symbol/resolve") {
  fun `test resolve identifier without declaration`() = doTest()

  fun `test resolve identifier reference with one declaration`() = doTest()

  fun `test resolve unique declaration with references`() = doTest()

  fun `test resolve identifier reference with several declarations`() = doTest()

  fun `test resolve several declarations`() = doTest()

  private fun doTest() {
    val testName = getTestName(true)
    val contentFile = "$testName.mermaid"
    val expectedFile = "${testName}.txt"
    myFixture.configureByFile(contentFile)
    val references = findReferencesAtCaret()
    val resolved = references.flatMap { it.resolveReference().filterIsInstance<MermaidSymbol>() }
    val rangeList = resolved.sortedBy { it.range.startOffset }.map { "${it.range} |${it.searchText}|" }
    val text = rangeList.joinToString(separator = "\n", postfix = "\n")
    val expectedFilePath = "$testDataPath/$expectedFile"
    val expected = Path(expectedFilePath).readText()
    TestCase.assertEquals(expected, text)
  }

  private fun findReferencesAtCaret(): Collection<PsiSymbolReference> {
    val referenceProvider = IdentifierSymbolReferenceProvider()
    val elements = myFixture.file.elementsAtOffsetUp(myFixture.caretOffset)
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
