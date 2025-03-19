package org.intellij.plugins.markdown.model

import com.intellij.model.psi.PsiSymbolDeclaration
import com.intellij.psi.util.elementsAtOffsetUp
import com.intellij.refactoring.rename.api.RenameTarget
import com.intellij.refactoring.rename.symbol.RenameableSymbol
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.intellij.plugins.markdown.MarkdownTestingUtil
import org.intellij.plugins.markdown.model.psi.headers.HeaderSymbolDeclarationProvider

abstract class HeaderSymbolTest(private val testDataPath: String): BasePlatformTestCase() {
  protected open fun obtainBeforeAfterNames(): Pair<String, String> {
    val testName = getTestName(true)
    return "$testName.md" to "${testName}_after.md"
  }

  protected fun renameSymbolTest() {
    val (before, after) = obtainBeforeAfterNames()
    myFixture.configureByFile(before)
    val newName = "New header name"
    renameByDeclaration(newName)
    myFixture.checkResultByFile(after)
  }

  private fun renameByDeclaration(newName: String) {
    val declaration = findSymbolDeclarationAtCaret().firstOrNull() ?: return
    val target = when (val symbol = declaration.symbol) {
      is RenameTarget -> symbol
      is RenameableSymbol -> symbol.renameTarget
      else -> null
    }
    checkNotNull(target) { "Failed to find rename target at caret" }
    myFixture.renameTarget(target, newName)
  }

  private fun findSymbolDeclarationAtCaret(): Collection<PsiSymbolDeclaration> {
    val provider = HeaderSymbolDeclarationProvider()
    val elements = myFixture.file.elementsAtOffsetUp(myFixture.caretOffset)
    for ((element, offset) in elements) {
      val declarations = provider.getDeclarations(element, offset)
      if (declarations.isNotEmpty()) {
        return declarations
      }
    }
    return emptyList()
  }

  override fun getTestName(lowercaseFirstLetter: Boolean): String {
    val name = super.getTestName(lowercaseFirstLetter)
    return name.trimStart().replace(' ', '_')
  }

  override fun getTestDataPath(): String {
    return "${MarkdownTestingUtil.TEST_DATA_PATH}/$testDataPath"
  }
}