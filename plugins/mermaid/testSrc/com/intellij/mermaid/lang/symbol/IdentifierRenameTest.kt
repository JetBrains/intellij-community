package com.intellij.mermaid.lang.symbol

import com.intellij.mermaid.lang.MermaidBaseTestCase
import com.intellij.mermaid.lang.psi.symbol.identifier.IdentifierSymbolDeclarationProvider
import com.intellij.mermaid.lang.psi.symbol.identifier.IdentifierSymbolReferenceProvider
import com.intellij.model.Symbol
import com.intellij.model.psi.PsiExternalReferenceHost
import com.intellij.model.psi.PsiSymbolReferenceHints
import com.intellij.psi.util.elementsAtOffsetUp
import com.intellij.refactoring.rename.api.RenameTarget
import com.intellij.refactoring.rename.symbol.RenameableSymbol
import com.intellij.refactoring.rename.symbol.SymbolRenameTargetFactory

@Suppress("UnstableApiUsage")
class IdentifierRenameTest : MermaidBaseTestCase("symbol/rename") {
  fun `test rename identifier without declaration`() = doTest()

  fun `test rename identifier reference with one declaration`() = doTest()

  fun `test rename unique declaration with references`() = doTest()

  fun `test rename identifier reference with several declarations`() = doTest()

  fun `test rename several declarations`() = doTest()

  fun `test rename quoted`() = doTest()

  private fun doTest() {
    val testName = getTestName(true)
    val before = testName + "_before.mermaid"
    val after = testName + "_after.mermaid"
    myFixture.configureByFile(before)
    val newName = "newName"
    doRename(newName)
    myFixture.checkResultByFile(after)
  }

  private fun findRenameTarget(symbol: Symbol): RenameTarget? {
    for (factory in SymbolRenameTargetFactory.EP_NAME.extensionList) {
      return factory.renameTarget(project, symbol) ?: continue
    }
    return when (symbol) {
      is RenameableSymbol -> symbol.renameTarget
      is RenameTarget -> symbol
      else -> null
    }
  }

  private fun doRename(newName: String) {
    val symbol = findSymbolAtCaret() ?: return
    val target = findRenameTarget(symbol)
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
}
