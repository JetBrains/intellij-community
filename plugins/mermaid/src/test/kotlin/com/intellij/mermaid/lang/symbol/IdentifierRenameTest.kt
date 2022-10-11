package com.intellij.mermaid.lang.symbol

import com.intellij.mermaid.lang.psi.symbol.identifier.IdentifierSymbolDeclarationProvider
import com.intellij.mermaid.lang.psi.symbol.identifier.IdentifierSymbolReferenceProvider
import com.intellij.model.Symbol
import com.intellij.model.psi.PsiExternalReferenceHost
import com.intellij.model.psi.PsiSymbolReferenceHints
import com.intellij.psi.util.elementsAtOffsetUp
import com.intellij.refactoring.rename.api.RenameTarget
import com.intellij.refactoring.rename.symbol.RenameableSymbol
import com.intellij.testFramework.fixtures.BasePlatformTestCase

@Suppress("UnstableApiUsage")
class IdentifierRenameTest : BasePlatformTestCase() {
  fun `test rename identifier without declaration`() = doTest()

  fun `test rename identifier reference with one declaration`() = doTest()

  fun `test rename unique declaration with references`() = doTest()

  fun `test rename identifier reference with several declarations`() = doTest()

  fun `test rename several declarations`() = doTest()

  private fun doTest() {
    val testName = getTestName(true)
    val before = testName + "_before.mermaid"
    val after = testName + "_after.mermaid"
    myFixture.configureByFile(before)
    val newName = "newName"
    doRename(newName)
    myFixture.checkResultByFile(after)
  }

  private fun doRename(newName: String) {
    val symbol = findSymbolAtCaret() ?: return
    val target = when (symbol) {
      is RenameTarget -> symbol
      is RenameableSymbol -> symbol.renameTarget
      else -> null
    }
    checkNotNull(target) { "Failed to find rename target at caret" }
    myFixture.renameTarget(target, newName)
  }

  private fun findSymbolAtCaret(): Symbol? {
    val declarationProvider = IdentifierSymbolDeclarationProvider()
    val referenceProvider = IdentifierSymbolReferenceProvider()
    val elements = myFixture.file.elementsAtOffsetUp(myFixture.caretOffset)
    for ((element, offset) in elements) {
      val declarations = declarationProvider.getDeclarations(element, offset)
      if (declarations.firstOrNull() != null) {
        return declarations.firstOrNull()!!.symbol
      }
      if (element is PsiExternalReferenceHost) {
        val references = referenceProvider.getReferences(element, PsiSymbolReferenceHints.offsetHint(offset))
        if (references.isNotEmpty()) {
          val firstOrNull = references.first().resolveReference().firstOrNull()
          if (firstOrNull != null) {
            return firstOrNull
          }
        }
      }
    }
    return null
  }

  override fun getTestName(lowercaseFirstLetter: Boolean): String {
    val name = super.getTestName(lowercaseFirstLetter)
    return name.trimStart().replace(' ', '_')
  }

  override fun getTestDataPath(): String {
    return "src/test/resources/symbol/rename"
  }
}
