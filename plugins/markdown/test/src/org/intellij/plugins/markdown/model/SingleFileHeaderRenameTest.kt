package org.intellij.plugins.markdown.model

import com.intellij.model.psi.PsiSymbolDeclaration
import com.intellij.openapi.application.readAction
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.codeInsightModuleFixture
import com.intellij.psi.util.elementsAtOffsetUp
import com.intellij.refactoring.rename.api.RenameTarget
import com.intellij.refactoring.rename.symbol.RenameableSymbol
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.TestDataPath
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import org.intellij.plugins.markdown.model.psi.headers.HeaderSymbolDeclarationProvider
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInfo

@TestApplication
@TestDataPath($$"$PROJECT_ROOT/community/plugins/markdown/test/data/model/headers/rename/file")
class SingleFileHeaderRenameTest {
  companion object {
    private val projectFixture = projectFixture(openAfterCreation = true)
  }

  private val myFixture by codeInsightModuleFixture(projectFixture)

  private lateinit var testName: String

  @BeforeEach
  fun setUp(testInfo: TestInfo) {
    testName = PlatformTestUtil.getTestName(testInfo.testMethod.get().name, true).trimStart().replace(' ', '_')
  }

  @Test
  fun `rename header with single reference`(): Unit = renameSymbolTest()

  @Test
  fun `rename header with multiple references`(): Unit = renameSymbolTest()

  @Test
  fun `rename header with single uppercase anchor`(): Unit = renameSymbolTest()

  private fun renameSymbolTest() {
    myFixture.configureByFile("$testName.md")
    val newName = "New header name"
    renameByDeclaration(newName)
    myFixture.checkResultByFile("${testName}_after.md")
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

  private fun findSymbolDeclarationAtCaret(): Collection<PsiSymbolDeclaration> = timeoutRunBlocking {
    readAction {
      val provider = HeaderSymbolDeclarationProvider()
      val elements = myFixture.file.elementsAtOffsetUp(myFixture.caretOffset)
      for ((element, offset) in elements) {
        val declarations = provider.getDeclarations(element, offset)
        if (declarations.isNotEmpty()) {
          return@readAction declarations
        }
      }
      emptyList()
    }
  }
}
