package org.intellij.plugins.markdown.reference

import com.intellij.openapi.application.readActionBlocking
import com.intellij.openapi.paths.WebReference
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.codeInsightModuleFixture
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiReference
import com.intellij.psi.impl.source.resolve.reference.impl.PsiMultiReference
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import org.assertj.core.api.SoftAssertions
import org.assertj.core.api.junit.jupiter.InjectSoftAssertions
import org.assertj.core.api.junit.jupiter.SoftAssertionsExtension
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@TestApplication
@ExtendWith(SoftAssertionsExtension::class)
class AutoLinkWebReferencesTest {
  companion object {
    private val projectFixture = projectFixture(openAfterCreation = true)
  }

  private val myFixture by codeInsightModuleFixture(projectFixture)

  @InjectSoftAssertions
  lateinit var softAssertions: SoftAssertions

  @Test
  fun `test commonmark autolink`() {
    val content = """
    Some auto link <https://<caret>jetbrains.com>
    """.trimIndent()
    doTest(content)
  }

  @Test
  fun `test gfm autolink`() {
    val content = """
    Some auto link https://<caret>jetbrains.com
    """.trimIndent()
    doTest(content)
  }

  @Test
  fun `test email autolink`() {
    val content = """
    Some auto link <someone@<caret>jetbrains.com>
    """.trimIndent()
    doTest(content)
  }

  private fun findReferences(file: PsiFile, offset: Int): Sequence<PsiReference> {
    val reference = file.findReferenceAt(offset) ?: return emptySequence()
    return when (reference) {
      is PsiMultiReference -> reference.references.asSequence()
      else -> sequenceOf(reference)
    }
  }

  private fun doTest(content: String): Unit = timeoutRunBlocking {
    myFixture.configureByText("some.md", content)
    readActionBlocking {
      val offset = myFixture.editor.caretModel.offset
      val references = findReferences(myFixture.file, offset)
      assertTrue(references.any(), "There were no references at given offset [$offset]")
      val webReferences = references.filterIsInstance<WebReference>()
      assertTrue(webReferences.any(), "There were no WebReferences at given offset [$offset]")
      for (reference in webReferences) {
        softAssertions.assertThat(reference.resolve())
          .overridingErrorMessage { "Reference should resolve to something" }
          .isNotNull
      }
    }
  }
}
