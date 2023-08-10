package org.intellij.plugins.markdown.reference

import com.intellij.openapi.paths.WebReference
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiReference
import com.intellij.psi.impl.source.resolve.reference.impl.PsiMultiReference
import com.intellij.testFramework.LightPlatformCodeInsightTestCase
import org.assertj.core.api.JUnitSoftAssertions
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class AutoLinkWebReferencesTest: LightPlatformCodeInsightTestCase() {
  @get:Rule
  val softAssertions = JUnitSoftAssertions()

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

  private fun doTest(content: String) {
    configureFromFileText("some.md", content)
    val offset = editor.caretModel.offset
    val references = findReferences(file, offset)
    assertTrue("There were no references at given offset [$offset]", references.any())
    val webReferences = references.filterIsInstance<WebReference>()
    assertTrue("There were no WebReferences at given offset [$offset]", webReferences.any())
    for (reference in webReferences) {
      softAssertions.assertThat(reference.resolve())
        .overridingErrorMessage { "Reference should resolve to something" }
        .isNotNull
    }
  }
}
