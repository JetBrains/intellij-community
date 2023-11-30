package org.intellij.plugins.markdown.model

import com.intellij.model.psi.PsiSymbolReferenceService
import com.intellij.openapi.components.service
import com.intellij.openapi.options.advanced.AdvancedSettings
import com.intellij.psi.PsiFile
import com.intellij.psi.util.parents
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.assertj.core.api.Assertions.assertThat
import org.intellij.plugins.markdown.MarkdownTestingUtil
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownLinkDestination
import org.intellij.plugins.markdown.model.psi.headers.HeaderAnchorLinkDestinationReference
import org.intellij.plugins.markdown.model.psi.headers.HeaderSymbol
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class HeaderAnchorSymbolResolveTest: BasePlatformTestCase() {
  override fun setUp() {
    super.setUp()
    myFixture.copyDirectoryToProject("", "")
  }

  private fun findLinkDestination(file: PsiFile, offset: Int): MarkdownLinkDestination? {
    val elementUnderCaret = file.findElementAt(offset)
    checkNotNull(elementUnderCaret)
    return elementUnderCaret.parents(withSelf = true).filterIsInstance<MarkdownLinkDestination>().firstOrNull()
  }

  @Test
  fun `reference to own header is resolved`() = doTest("own-header")

  @Test
  fun `reference to header in other file is resolved`() = doTest("header-near-main")

  @Test
  fun `reference to header in subdirectory is resolved`() = doTest("header-in-subdirectory")

  @Test
  fun `reference to header in deep subdirectory is resolved`() = doTest("some-deep-header")

  @Test
  fun `reference to header in list is resolved`() = doTest("header-inside-list-item")

  @Test
  fun `reference to header in other file without extension is resolved`() = doTest("header-near-main")

  @Test
  fun `reference with uppercase part is resolved`() = doTest("own-header")

  @Test
  fun `special gfm case`() = doTest("get-method")

  @Test
  fun `weird date case`() = doTest("100-april-8-2018")

  @Test
  fun `test header with non characters`() = doTest("this-is-a-header-with--and--or---and-also-_-inside")

  @Test
  fun `test header with non characters gitlab`() = run {
    val oldValue = AdvancedSettings.getBoolean("markdown.squash.multiple.dashes.in.header.anchors")
    try {
      AdvancedSettings.setBoolean("markdown.squash.multiple.dashes.in.header.anchors", true)
      doTest("this-is-a-header-with-and-or-and-also-_-inside")
    } finally {
      AdvancedSettings.setBoolean("markdown.squash.multiple.dashes.in.header.anchors", oldValue)
    }
  }

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
    assertThat(targetReferences)
      .withFailMessage { "Failed to collect symbol references for ${linkDestination.text}" }
      .isNotEmpty
    val resolved = targetReferences.flatMap { it.resolveReference().filterIsInstance<HeaderSymbol>() }
    assertThat(resolved)
      .withFailMessage { "Failed to resolve symbol reference for ${linkDestination.text}" }
      .isNotEmpty
    val header = resolved.find { it.anchorText == expectedAnchor }
    assertThat(header)
      .withFailMessage {
        "Anchor reference resolved to something else: ${resolved.map { "${it.anchorText} - ${it.text}" }}\n" +
        "Expected resolution result: $expectedAnchor"
      }.isNotNull
  }

  override fun getTestName(lowercaseFirstLetter: Boolean): String {
    val name = super.getTestName(lowercaseFirstLetter)
    return name.trimStart().replace(' ', '_')
  }

  override fun getTestDataPath(): String {
    return "${MarkdownTestingUtil.TEST_DATA_PATH}/model/headers/resolve"
  }
}
