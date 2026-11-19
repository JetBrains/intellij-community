package org.intellij.plugins.markdown.model

import com.intellij.model.psi.PsiSymbolReferenceService
import com.intellij.openapi.application.readAction
import com.intellij.openapi.components.service
import com.intellij.openapi.options.advanced.AdvancedSettings
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.codeInsightModuleFixture
import com.intellij.psi.PsiFile
import com.intellij.psi.util.parents
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.TestDataPath
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import org.assertj.core.api.Assertions.assertThat
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownLinkDestination
import org.intellij.plugins.markdown.model.psi.headers.HeaderAnchorLinkDestinationReference
import org.intellij.plugins.markdown.model.psi.headers.HeaderSymbol
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInfo

@TestApplication
@TestDataPath($$"$PROJECT_ROOT/community/plugins/markdown/test/data/model/headers/resolve")
class HeaderAnchorSymbolResolveTest {
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

  private fun findLinkDestination(file: PsiFile, offset: Int): MarkdownLinkDestination? {
    val elementUnderCaret = file.findElementAt(offset)
    checkNotNull(elementUnderCaret)
    return elementUnderCaret.parents(withSelf = true).filterIsInstance<MarkdownLinkDestination>().firstOrNull()
  }

  @Test
  fun `reference to own header is resolved`(): Unit = doTest("own-header")

  @Test
  fun `reference to header in other file is resolved`(): Unit = doTest("header-near-main")

  @Test
  fun `reference to header in subdirectory is resolved`(): Unit = doTest("header-in-subdirectory")

  @Test
  fun `reference to header in deep subdirectory is resolved`(): Unit = doTest("some-deep-header")

  @Test
  fun `reference to header in list is resolved`(): Unit = doTest("header-inside-list-item")

  @Test
  fun `reference to header in other file without extension is resolved`(): Unit = doTest("header-near-main")

  @Test
  fun `reference with uppercase part is resolved`(): Unit = doTest("own-header")

  @Test
  fun `special gfm case`(): Unit = doTest("get-method")

  @Test
  fun `weird date case`(): Unit = doTest("100-april-8-2018")

  @Test
  fun `test header with non characters`(): Unit = doTest("this-is-a-header-with--and--or---and-also-_-inside")

  @Test
  fun `test header with non characters gitlab`(): Unit = run {
    val oldValue = AdvancedSettings.getBoolean("markdown.squash.multiple.dashes.in.header.anchors")
    try {
      AdvancedSettings.setBoolean("markdown.squash.multiple.dashes.in.header.anchors", true)
      doTest("this-is-a-header-with-and-or-and-also-_-inside")
    } finally {
      AdvancedSettings.setBoolean("markdown.squash.multiple.dashes.in.header.anchors", oldValue)
    }
  }

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
    }
  }
}
