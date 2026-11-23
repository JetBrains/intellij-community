package org.intellij.plugins.markdown.completion

import com.intellij.openapi.application.EDT
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.codeInsightModuleFixture
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.TestDataPath
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInfo

@TestApplication
@TestDataPath($$"$PROJECT_ROOT/community/plugins/markdown/test/data/completion/linkLabel")
class LinkLabelCompletionTest {
  companion object {
    private val projectFixture = projectFixture(openAfterCreation = true)
  }

  private val myFixture by codeInsightModuleFixture(projectFixture)

  private lateinit var testName: String

  @BeforeEach
  fun setUp(testInfo: TestInfo) {
    testName = PlatformTestUtil.getTestName(testInfo.testMethod.get().name, true)
  }

  private fun completionVariants(): List<String> {
    return myFixture.getCompletionVariants(testName + ".md").orEmpty()
  }

  @Test
  fun testDefinitionContext() {
    assertThat(completionVariants()).contains("alpha", "beta")
  }

  @Test
  fun testReferenceContext() {
    assertThat(completionVariants()).contains("alpha", "beta")
  }

  @Test
  fun testReferenceContextClosed() {
    assertThat(completionVariants()).contains("alpha", "beta")
  }

  @Test
  fun testDefinitionExcludesDefined() {
    val variants = completionVariants()
    assertThat(variants).contains("beta")
    assertThat(variants).doesNotContain("alpha")
  }

  @Test
  fun testReferenceExcludesUndefined() {
    val variants = completionVariants()
    assertThat(variants).contains("alpha")
    assertThat(variants).doesNotContain("beta")
  }

  @Test
  fun testFootnoteExcluded() {
    val variants = completionVariants()
    assertThat(variants).contains("alpha", "gamma")
    assertThat(variants).doesNotContain("note", "^note", "[^note]")
  }

  @Test
  fun testLinkTextPositionOffersNothing() {
    assertThat(completionVariants()).doesNotContain("some-link")
  }

  @Test
  fun testCodeSpanOffersNothing() {
    assertThat(completionVariants()).doesNotContain("some-link")
  }

  @Test
  fun testClosedReferenceInsertion(): Unit = timeoutRunBlocking {
    myFixture.configureByFile("closedReferenceInsertion.md")
    withContext(Dispatchers.EDT) {
      val elements = myFixture.completeBasic()
      if (elements != null) {
        val target = elements.firstOrNull { it.lookupString == "only-link" }
        requireNotNull(target) { "Expected 'only-link' among completion variants: ${elements.map { it.lookupString }}" }
        myFixture.lookup.currentItem = target
        myFixture.type('\n')
      }
      myFixture.checkResultByFile("closedReferenceInsertion_after.md")
    }
  }
}