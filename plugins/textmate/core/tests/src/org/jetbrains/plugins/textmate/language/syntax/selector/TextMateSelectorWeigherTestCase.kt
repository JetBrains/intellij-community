package org.jetbrains.plugins.textmate.language.syntax.selector

import org.jetbrains.plugins.textmate.TestUtil
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

abstract class TextMateSelectorWeigherTestCase {
  protected abstract fun <T> withWeigher(body: (TextMateSelectorWeigher) -> T): T

  @Test
  fun testPositiveWeigh() {
    val selector = "text.html.basic source.php.embedded.html string.quoted.double.php"
    assertGreaterThan(getWeigh("source.php string", selector), 0)
    assertGreaterThan(getWeigh("text.html source.php", selector), 0)
  }

  @Test
  fun testDoNotMatchPartialPrefix() {
    val selector = "source.rust string"
    assertEquals(0, getWeigh("source.r string", selector))
  }

  @Test
  fun testNegativeWeigh() {
    val selector = "text.html.basic source.php.embedded.html string.quoted.double.php"
    assertEquals(0, getWeigh("string source.php", selector))
    assertEquals(0, getWeigh("source.php text.html", selector))
  }

  @Test
  fun testSelectorInfluence() {
    val selector = "text.html.basic source.php.embedded.html string.quoted.double.php"
    assertGreaterThan(getWeigh("string", selector), getWeigh("source.php", selector))
  }

  @Test
  fun testNestingInfluence() {
    val selector = "text.html.basic source.php.embedded.html string.quoted.double.php"
    assertGreaterThan(getWeigh("string.quoted", selector), getWeigh("source.php", selector))
  }

  @Test
  fun testMatchesCountInfluence() {
    val selector = "text.html.basic source.php.embedded.html string.quoted.double.php"
    assertGreaterThan(getWeigh("text source string", selector), getWeigh("source string", selector))
  }

  @Test
  fun testPositiveExcludingSelector() {
    val selector = "text.html.basic source.php.embedded.html string.quoted.double.php"
    assertEquals(0, getWeigh("text.html source.php - string", selector))
  }

  @Test
  fun testNegativeExcludingSelector() {
    val selector = "text.html.basic source.php.embedded.html string.quoted.double.php"
    assertGreaterThan(getWeigh("text.html source.php - ruby", selector), 0)
  }

  @Test
  fun testMatch() {
    val selector = "foo"
    assertGreaterThan(getWeigh(selector, "foo"), 0)
  }

  @Test
  fun testNonMatch() {
    val selector = "foo"
    assertEquals(0, getWeigh(selector, "bar"))
  }

  @Test
  fun testExcludeNonMatch() {
    val selector = "- foo"
    assertEquals(0, getWeigh(selector, "foo"))
  }

  @Test
  fun testExcludeMatch() {
    val selector = "- foo"
    assertGreaterThan(getWeigh(selector, "bar"), 0)
  }

  @Test
  fun testDoubleExcludeNonMatch() {
    val selector = "- - foo"
    assertEquals(0, getWeigh(selector, "bar"))
  }

  @Test
  fun testComplexSelector_1() {
    val selector = "bar foo"
    assertEquals(0, getWeigh(selector, "foo"))
  }

  @Test
  fun testComplexSelector_2() {
    val selector = "bar foo"
    assertEquals(0, getWeigh(selector, "bar"))
  }

  @Test
  fun testComplexSelectorMatch() {
    val selector = "bar foo"
    assertGreaterThan(getWeigh(selector, "bar foo"), 0)
  }

  @Test
  fun testComplexWithExcludeMatch() {
    val selector = "bar - foo"
    assertGreaterThan(getWeigh(selector, "bar"), 0)
  }

  @Test
  fun testComplexWithExclude_1() {
    val selector = "bar - foo"
    assertEquals(0, getWeigh(selector, "foo bar"))
  }

  @Test
  fun testComplexWithExclude_2() {
    val selector = "bar - foo"
    assertEquals(0, getWeigh(selector, "foo"))
  }

  @Test
  fun testCoupleSelectors_1() {
    val selector = "bar, foo"
    assertGreaterThan(getWeigh(selector, "bar"), 0)
  }

  @Test
  fun testCoupleSelectors_2() {
    val selector = "bar, foo"
    assertGreaterThan(getWeigh(selector, "bar foo"), 0)
  }

  @Test
  fun testCoupleSelectors_3() {
    val selector = "bar, foo"
    assertGreaterThan(getWeigh(selector, "foo"), 0)
  }

  @Test
  fun testCoupleSelectorsWithExclude_3() {
    val selector = "bar, -foo"
    assertEquals(0, getWeigh(selector, "foo"))
  }

  @Test
  fun testParens() {
    val selector = "(foo)"
    assertGreaterThan(getWeigh(selector, "foo"), 0)
  }

  @Test
  fun testParensWithExclude_1() {
    val selector = "(foo - bar)"
    assertGreaterThan(getWeigh(selector, "foo"), 0)
  }

  @Test
  fun testParensWithExclude_2() {
    val selector = "(foo - bar)"
    assertEquals(0, getWeigh(selector, "foo bar"))
  }

  @Test
  fun testParensWithExclude_3() {
    val selector = "foo bar - (yo man)"
    assertGreaterThan(getWeigh(selector, "foo bar"), 0)
  }

  @Test
  fun testParensWithExclude_4() {
    val selector = "foo bar - (yo man)"
    assertGreaterThan(getWeigh(selector, "foo bar yo"), 0)
  }

  @Test
  fun testParensWithExclude_5() {
    val selector = "foo bar - (yo man)"
    assertEquals(0, getWeigh(selector, "foo bar yo man"))
  }

  @Test
  fun testConjunction_1() {
    val selector = "foo bar - (yo | man)"
    assertEquals(0, getWeigh(selector, "foo bar yo man"))
  }

  @Test
  fun testConjunction_2() {
    val selector = "foo bar - (yo | man)"
    assertEquals(0, getWeigh(selector, "foo bar yo"))
  }

  @Test
  fun testPriorityPrefix_1() {
    val selector = "R:text.html - (comment.block, text.html source)"
    assertEquals(0, getWeigh(selector, "text.html bar source"))
  }

  @Test
  fun testPriorityPrefix_2() {
    assertGreaterThan(getWeigh("text.html.php - (meta.embedded | meta.tag), L:text.html.php meta.tag, L:source.js.embedded.html",
                               "text.html.php bar source.js"), 0)
  }

  @Test
  fun testStartMatch() {
    val selector = "R:^text.html"
    assertGreaterThan(getWeigh(selector, "text.html bar source"), 0)
    assertEquals(0, getWeigh(selector, "foo text.html bar source"))
  }

  @Test
  fun testAstroScriptSelector() {
    val selector = "L:meta.script.astro - meta.lang - (meta source)"
    val scope = "source.astro meta.scope.tag.script.astro meta.script.astro meta.embedded.block.astro source.js"

    assertEquals(0, getWeigh(selector, scope))
  }

  private fun getWeigh(selector: String, scopeString: String): Int {
    return withWeigher {
      it.weigh(selector, TestUtil.scopeFromString(scopeString)).weigh
    }
  }

  private fun assertGreaterThan(firstValue: Int, secondValue: Int) {
    assertTrue(firstValue > secondValue, "$firstValue > $secondValue")
  }
}
