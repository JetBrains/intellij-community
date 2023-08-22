package org.intellij.plugins.markdown.model

import com.intellij.model.psi.PsiSymbolReferenceService
import com.intellij.openapi.components.service
import com.intellij.psi.PsiFile
import com.intellij.psi.util.parents
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.assertj.core.api.Assertions
import org.intellij.plugins.markdown.MarkdownTestingUtil
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownLinkDestination
import org.intellij.plugins.markdown.model.psi.headers.HeaderAnchorLinkDestinationReference
import org.intellij.plugins.markdown.model.psi.headers.html.HtmlAnchorSymbol
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/**
 * Same as [HeaderAnchorSymbolResolveTest], but tests sanity of html ids used as anchors in link destinations.
 */
@RunWith(JUnit4::class)
class HtmlAnchorSymbolResolveTest: BasePlatformTestCase() {
  override fun setUp() {
    super.setUp()
    myFixture.copyDirectoryToProject("", "")
  }

  @Test
  fun `reference to own header is resolved`() = doTest("own-header")

  @Test
  fun `reference to header in other file is resolved`() = doTest("header-near-main")

  private fun doTest(expectedAnchor: String) {
    val testName = getTestName(true)
    val file = myFixture.configureFromTempProjectFile("$testName.md")
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

  private fun findLinkDestination(file: PsiFile, offset: Int): MarkdownLinkDestination? {
    val elementUnderCaret = file.findElementAt(offset)
    checkNotNull(elementUnderCaret)
    return elementUnderCaret.parents(withSelf = true).filterIsInstance<MarkdownLinkDestination>().firstOrNull()
  }

  override fun getTestName(lowercaseFirstLetter: Boolean): String {
    val name = super.getTestName(lowercaseFirstLetter)
    return name.trimStart().replace(' ', '_')
  }

  override fun getTestDataPath(): String {
    return "${MarkdownTestingUtil.TEST_DATA_PATH}/model/headers/resolve/html-anchors"
  }
}
