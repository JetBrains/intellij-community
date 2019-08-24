package de.plushnikov.intellij.plugin.processor;

import de.plushnikov.intellij.plugin.AbstractLombokParsingTestCase;

/**
 * Unit tests for @SuperBuilder support
 */
public class SuperBuilderTest extends AbstractLombokParsingTestCase {

  @Override
  protected String annotationToComparePattern() {
    return "java.lang.Deprecated";
  }

  @Override
  public void setUp() throws Exception {
    super.setUp();
    // Add dummy classes, which are absent in mockJDK
    myFixture.addClass("package java.util;\n  public interface NavigableMap<K,V> extends java.util.SortedMap<K,V> {}");
  }

  public void testSuperbuilder$SuperBuilderSimple() {
    doTest(true);
  }

}
