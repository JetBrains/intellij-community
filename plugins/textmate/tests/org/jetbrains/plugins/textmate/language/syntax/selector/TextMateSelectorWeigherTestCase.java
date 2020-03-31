package org.jetbrains.plugins.textmate.language.syntax.selector;

import org.jetbrains.plugins.textmate.language.syntax.selector.TextMateSelectorWeigher;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

abstract public class TextMateSelectorWeigherTestCase {
  private TextMateSelectorWeigher weigher;

  protected abstract TextMateSelectorWeigher createWeigher();

  @Before
  public void setUp() {
    weigher = createWeigher();
  }

  @Test
  public void testPositiveWeigh() {
    String selector = "text.html.basic source.php.embedded.html string.quoted.double.php";
    assertGreaterThan(getWeigh("source.php string", selector), 0);
    assertGreaterThan(getWeigh("text.html source.php", selector), 0);
  }

  @Test
  public void testNegativeWeigh() {
    String selector = "text.html.basic source.php.embedded.html string.quoted.double.php";
    assertEquals(0, getWeigh("string source.php", selector));
    assertEquals(0, getWeigh("source.php text.html", selector));
  }

  @Test
  public void testSelectorInfluence() {
    String selector = "text.html.basic source.php.embedded.html string.quoted.double.php";
    assertGreaterThan(getWeigh("string", selector), getWeigh("source.php", selector));
  }

  @Test
  public void testNestingInfluence() {
    String selector = "text.html.basic source.php.embedded.html string.quoted.double.php";
    assertGreaterThan(getWeigh("string.quoted", selector), getWeigh("source.php", selector));
  }

  @Test
  public void testMatchesCountInfluence() {
    String selector = "text.html.basic source.php.embedded.html string.quoted.double.php";
    assertGreaterThan(getWeigh("text source string", selector), getWeigh("source string", selector));
  }

  @Test
  public void testPositiveExcludingSelector() {
    String selector = "text.html.basic source.php.embedded.html string.quoted.double.php";
    assertEquals(0, getWeigh("text.html source.php - string", selector));
  }

  @Test
  public void testNegativeExcludingSelector() {
    String selector = "text.html.basic source.php.embedded.html string.quoted.double.php";
    assertGreaterThan(getWeigh("text.html source.php - ruby", selector), 0);
  }

  @Test
  public void testMatch() {
    String selector = "foo";
    assertGreaterThan(getWeigh(selector, "foo"), 0);
  }

  @Test
  public void testNonMatch() {
    String selector = "foo";
    assertEquals(0, getWeigh(selector, "bar"));
  }

  @Test
  public void testExcludeNonMatch() {
    String selector = "- foo";
    assertEquals(0, getWeigh(selector, "foo"));
  }

  @Test
  public void testExcludeMatch() {
    String selector = "- foo";
    assertGreaterThan(getWeigh(selector, "bar"), 0);
  }

  @Test
  public void testDoubleExcludeNonMatch() {
    String selector = "- - foo";
    assertEquals(0, getWeigh(selector, "bar"));
  }

  @Test
  public void testComplexSelector_1() {
    String selector = "bar foo";
    assertEquals(0, getWeigh(selector, "foo"));
  }

  @Test
  public void testComplexSelector_2() {
    String selector = "bar foo";
    assertEquals(0, getWeigh(selector, "bar"));
  }

  @Test
  public void testComplexSelectorMatch() {
    String selector = "bar foo";
    assertGreaterThan(getWeigh(selector, "bar foo"), 0);
  }

  @Test
  public void testComplexWithExcludeMatch() {
    String selector = "bar - foo";
    assertGreaterThan(getWeigh(selector, "bar"), 0);
  }

  @Test
  public void testComplexWithExclude_1() {
    String selector = "bar - foo";
    assertEquals(0, getWeigh(selector, "foo bar"));
  }

  @Test
  public void testComplexWithExclude_2() {
    String selector = "bar - foo";
    assertEquals(0, getWeigh(selector, "foo"));
  }

  @Test
  public void testCoupleSelectors_1() {
    String selector = "bar, foo";
    assertGreaterThan(getWeigh(selector, "bar"), 0);
  }

  @Test
  public void testCoupleSelectors_2() {
    String selector = "bar, foo";
    assertGreaterThan(getWeigh(selector, "bar foo"), 0);
  }

  @Test
  public void testCoupleSelectors_3() {
    String selector = "bar, foo";
    assertGreaterThan(getWeigh(selector, "foo"), 0);
  }

  @Test
  public void testCoupleSelectorsWithExclude_3() {
    String selector = "bar, -foo";
    assertEquals(0, getWeigh(selector, "foo"));
  }

  @Test
  public void testParens() {
    String selector = "(foo)";
    assertGreaterThan(getWeigh(selector, "foo"), 0);
  }

  @Test
  public void testParensWithExclude_1() {
    String selector = "(foo - bar)";
    assertGreaterThan(getWeigh(selector, "foo"), 0);
  }

  @Test
  public void testParensWithExclude_2() {
    String selector = "(foo - bar)";
    assertEquals(0, getWeigh(selector, "foo bar"));
  }

  @Test
  public void testParensWithExclude_3() {
    String selector = "foo bar - (yo man)";
    assertGreaterThan(getWeigh(selector, "foo bar"), 0);
  }

  @Test
  public void testParensWithExclude_4() {
    String selector = "foo bar - (yo man)";
    assertGreaterThan(getWeigh(selector, "foo bar yo"), 0);
  }

  @Test
  public void testParensWithExclude_5() {
    String selector = "foo bar - (yo man)";
    assertEquals(0, getWeigh(selector, "foo bar yo man"));
  }

  @Test
  public void testConjunction_1() {
    String selector = "foo bar - (yo | man)";
    assertEquals(0, getWeigh(selector, "foo bar yo man"));
  }

  @Test
  public void testConjunction_2() {
    String selector = "foo bar - (yo | man)";
    assertEquals(0, getWeigh(selector, "foo bar yo"));
  }

  @Test
  public void testPriorityPrefix_1() {
    String selector = "R:text.html - (comment.block, text.html source)";
    assertEquals(0, getWeigh(selector, "text.html bar source"));
  }

  @Test
  public void testPriorityPrefix_2() {
    assertGreaterThan(getWeigh("text.html.php - (meta.embedded | meta.tag), L:text.html.php meta.tag, L:source.js.embedded.html", "text.html.php bar source.js"), 0);
  }

  @Test
  public void testStartMatch() {
    String selector = "R:^text.html";
    assertGreaterThan(getWeigh(selector, "text.html bar source"), 0);
    assertEquals(0, getWeigh(selector, "foo text.html bar source"));
  }

  private int getWeigh(String selector, String scope) {
    return weigher.weigh(selector, scope).weigh;
  }

  private static void assertGreaterThan(int firstValue, int secondValue) {
    assertTrue(String.format("%d > %d", firstValue, secondValue), firstValue > secondValue);
  }
}
