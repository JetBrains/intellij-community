package org.intellij.plugins.markdown.completion

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.intellij.plugins.markdown.MarkdownTestingUtil

class LinkLabelCompletionTest: BasePlatformTestCase() {
  override fun getTestDataPath(): String = MarkdownTestingUtil.TEST_DATA_PATH + "/completion/linkLabel/"

  private fun completionVariants(): List<String> {
    return myFixture.getCompletionVariants(getTestName(true) + ".md").orEmpty()
  }

  fun testDefinitionContext() {
    assertContainsElements(completionVariants(), "alpha", "beta")
  }

  fun testReferenceContext() {
    assertContainsElements(completionVariants(), "alpha", "beta")
  }

  fun testReferenceContextClosed() {
    assertContainsElements(completionVariants(), "alpha", "beta")
  }

  fun testDefinitionExcludesDefined() {
    val variants = completionVariants()
    assertContainsElements(variants, "beta")
    assertDoesntContain(variants, "alpha")
  }

  fun testReferenceExcludesUndefined() {
    val variants = completionVariants()
    assertContainsElements(variants, "alpha")
    assertDoesntContain(variants, "beta")
  }

  fun testFootnoteExcluded() {
    val variants = completionVariants()
    assertContainsElements(variants, "alpha", "gamma")
    assertDoesntContain(variants, "note", "^note", "[^note]")
  }

  fun testLinkTextPositionOffersNothing() {
    assertDoesntContain(completionVariants(), "some-link")
  }

  fun testCodeSpanOffersNothing() {
    assertDoesntContain(completionVariants(), "some-link")
  }

  fun testClosedReferenceInsertion() {
    myFixture.configureByFile("closedReferenceInsertion.md")
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