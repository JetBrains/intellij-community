package com.intellij.util.xml;

import junit.framework.TestCase;

/**
 * @author peter
 */
public class NameStrategyTest extends TestCase {

  public void testHyphenStrategy() throws Throwable {
    assertEquals("aaa-bbb-ccc", DomNameStrategy.HYPHEN_STRATEGY.convertName("aaaBbbCcc"));
    assertEquals("aaa-bbb-ccc", DomNameStrategy.HYPHEN_STRATEGY.convertName("AaaBbbCcc"));
    assertEquals("aaa", DomNameStrategy.HYPHEN_STRATEGY.convertName("Aaa"));
    assertEquals("aaa", DomNameStrategy.HYPHEN_STRATEGY.convertName("aaa"));
    assertEquals("AAA-bbb", DomNameStrategy.HYPHEN_STRATEGY.convertName("AAABbb"));
    assertEquals("aaa-BBB", DomNameStrategy.HYPHEN_STRATEGY.convertName("AaaBBB"));
  }

  public void testHyphenStrategySplit() throws Throwable {
    assertEquals("aaa bbb ccc", DomNameStrategy.HYPHEN_STRATEGY.splitIntoWords("aaa-bbb-ccc"));
    assertEquals("Aaa", DomNameStrategy.HYPHEN_STRATEGY.splitIntoWords("Aaa"));
    assertEquals("aaa", DomNameStrategy.HYPHEN_STRATEGY.splitIntoWords("aaa"));
    assertEquals("AAA bbb", DomNameStrategy.HYPHEN_STRATEGY.splitIntoWords("AAA-bbb"));
    assertEquals("aaa BBB", DomNameStrategy.HYPHEN_STRATEGY.splitIntoWords("aaa-BBB"));
  }

  public void testJavaStrategy() throws Throwable {
    assertEquals("aaaBbbCcc", DomNameStrategy.JAVA_STRATEGY.convertName("aaaBbbCcc"));
    assertEquals("aaaBbbCcc", DomNameStrategy.JAVA_STRATEGY.convertName("AaaBbbCcc"));
    assertEquals("aaa", DomNameStrategy.JAVA_STRATEGY.convertName("Aaa"));
    assertEquals("aaa", DomNameStrategy.JAVA_STRATEGY.convertName("aaa"));
    assertEquals("AAABbb", DomNameStrategy.JAVA_STRATEGY.convertName("AAABbb"));
    assertEquals("aaaBBB", DomNameStrategy.JAVA_STRATEGY.convertName("AaaBBB"));
  }

  public void testJavaStrategySplit() throws Throwable {
    assertEquals("aaa bbb ccc", DomNameStrategy.JAVA_STRATEGY.splitIntoWords("aaaBbbCcc"));
    assertEquals("aaa", DomNameStrategy.JAVA_STRATEGY.splitIntoWords("Aaa"));
    assertEquals("aaa", DomNameStrategy.JAVA_STRATEGY.splitIntoWords("aaa"));
    assertEquals("AAA bbb", DomNameStrategy.JAVA_STRATEGY.splitIntoWords("AAABbb"));
    assertEquals("aaa BBB", DomNameStrategy.JAVA_STRATEGY.splitIntoWords("aaaBBB"));
  }

}
