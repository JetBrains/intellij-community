package com.intellij.mermaid.lang.symbol

import com.intellij.mermaid.lang.mermaidFixture
import com.intellij.mermaid.lang.psi.symbol.identifier.IdentifierSymbolDeclarationProvider
import com.intellij.mermaid.lang.psi.symbol.identifier.IdentifierSymbolReferenceProvider
import com.intellij.model.Symbol
import com.intellij.model.psi.PsiExternalReferenceHost
import com.intellij.model.psi.PsiSymbolReferenceHints
import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.psi.util.elementsAtOffsetUp
import com.intellij.refactoring.rename.api.RenameTarget
import com.intellij.refactoring.rename.symbol.RenameableSymbol
import com.intellij.refactoring.rename.symbol.SymbolRenameTargetFactory
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import org.junit.jupiter.api.Test

@Suppress("UnstableApiUsage")
@TestApplication
class IdentifierRenameTest {
  companion object {
    private val projectFixture = projectFixture(openAfterCreation = true)
  }

  private val mermaid by mermaidFixture(projectFixture, "symbol/rename")

  @Test
  fun `test rename identifier without declaration`(): Unit = doTest()

  @Test
  fun `test rename identifier reference with one declaration`(): Unit = doTest()

  @Test
  fun `test rename unique declaration with references`(): Unit = doTest()

  @Test
  fun `test rename identifier reference with several declarations`(): Unit = doTest()

  @Test
  fun `test rename several declarations`(): Unit = doTest()

  @Test
  fun `test rename quoted`(): Unit = doTest()

  private fun doTest() {
    val testName = mermaid.getTestName(true)
    val before = testName + "_before.mermaid"
    val after = testName + "_after.mermaid"
    mermaid.fixture.configureByFile(before)
    val newName = "newName"
    doRename(newName)
    mermaid.fixture.checkResultByFile(after)
  }

  private fun findRenameTarget(symbol: Symbol): RenameTarget? {
    for (factory in SymbolRenameTargetFactory.EP_NAME.extensionList) {
      return factory.renameTarget(mermaid.project, symbol) ?: continue
    }
    return when (symbol) {
      is RenameableSymbol -> symbol.renameTarget
      is RenameTarget -> symbol
      else -> null
    }
  }

  private fun doRename(newName: String) {
    val symbol = runReadActionBlocking { findSymbolAtCaret() } ?: return
    val target = runReadActionBlocking { findRenameTarget(symbol) }
    checkNotNull(target) { "Failed to find rename target at caret" }
    mermaid.fixture.renameTarget(target, newName)
  }

  private fun findSymbolAtCaret(): Symbol? {
    val declarationProvider = IdentifierSymbolDeclarationProvider()
    val referenceProvider = IdentifierSymbolReferenceProvider()
    val elements = mermaid.fixture.file.elementsAtOffsetUp(mermaid.fixture.caretOffset)
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
