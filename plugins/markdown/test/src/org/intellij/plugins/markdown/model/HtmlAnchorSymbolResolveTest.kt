package org.intellij.plugins.markdown.model

import com.intellij.model.psi.PsiSymbolReferenceService
import com.intellij.openapi.application.readAction
import com.intellij.openapi.components.service
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.codeInsightModuleFixture
import com.intellij.psi.PsiFile
import com.intellij.psi.util.parents
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.TestDataPath
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import org.assertj.core.api.Assertions
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownLinkDestination
import org.intellij.plugins.markdown.model.psi.headers.HeaderAnchorLinkDestinationReference
import org.intellij.plugins.markdown.model.psi.headers.html.HtmlAnchorSymbol
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInfo

/**
 * Same as [HeaderAnchorSymbolResolveTest], but tests sanity of html ids used as anchors in link destinations.
 */
@TestApplication
@TestDataPath($$"$PROJECT_ROOT/community/plugins/markdown/test/data/model/headers/resolve/html-anchors")
class HtmlAnchorSymbolResolveTest {
  companion object {
    private val projectFixture = projectFixture(openAfterCreation = true)
  }

  private val myFixture by codeInsightModuleFixture(projectFixture)

  private lateinit var testName: String

  @BeforeEach
  fun setUp(testInfo: TestInfo) {
    testName = PlatformTestUtil.getTestName(testInfo.testMethod.get().name, true).trimStart().replace(' ', '_')
    myFixture.copyDirectoryToProject("", "")
  }

  @Test
  fun `reference to own header is resolved`(): Unit = doTest("own-header")

  @Test
  fun `reference to own a-name anchor is resolved`(): Unit = doTest("contributing")

  @Test
  fun `reference to header in other file is resolved`(): Unit = doTest("header-near-main")

  private fun doTest(expectedAnchor: String) {
    val file = myFixture.configureFromTempProjectFile("$testName.md")
    timeoutRunBlocking {
      readAction {
        val caretModel = myFixture.editor.caretModel
        val caret = caretModel.primaryCaret
        val offset = caret.offset
        val linkDestination = findLinkDestination(file, offset)
        checkNotNull(linkDestination)
        val references = service<PsiSymbolReferenceService>().getReferences(linkDestination)
        val targetReferences = references.filterIsInstance<HeaderAnchorLinkDestinationReference>()
        Assertions.assertThat(targetReferences)
          .withFailMessage { "Failed to collect symbol references for ${linkDestination.text}" }
          .isNotEmpty
        val resolved = targetReferences.flatMap { it.resolveReference().filterIsInstance<HtmlAnchorSymbol>() }
        Assertions.assertThat(resolved)
          .withFailMessage { "Failed to resolve symbol reference for ${linkDestination.text}" }
          .isNotEmpty
        val header = resolved.find { it.anchorText == expectedAnchor }
        Assertions.assertThat(header)
          .withFailMessage {
            "Anchor reference resolved to something else: ${resolved.map { "${it.anchorText} - ${it.text}" }}\n" +
            "Expected resolution result: $expectedAnchor"
          }.isNotNull
      }
    }
  }

  private fun findLinkDestination(file: PsiFile, offset: Int): MarkdownLinkDestination? {
    val elementUnderCaret = file.findElementAt(offset)
    checkNotNull(elementUnderCaret)
    return elementUnderCaret.parents(withSelf = true).filterIsInstance<MarkdownLinkDestination>().firstOrNull()
  }
}
