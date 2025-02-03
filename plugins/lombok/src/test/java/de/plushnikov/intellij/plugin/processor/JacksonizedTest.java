package de.plushnikov.intellij.plugin.processor;

import de.plushnikov.intellij.plugin.AbstractLombokParsingTestCase;

/**
 * Unit tests for IntelliJPlugin for Lombok, based on lombok test classes
 */
public class JacksonizedTest extends AbstractLombokParsingTestCase {

  @Override
  protected boolean shouldCompareAnnotations() {
    return true;
  }

  @Override
  public void setUp() throws Exception {
    super.setUp();
    // Add dummy classes, which are absent in mockJDK
    myFixture.addClass("package java.util;\n  public interface NavigableMap<K,V> extends java.util.SortedMap<K,V> {}");
  }


  public void testJacksonized$JacksonizedBuilderSimple() {
    doTest(true);
  }

  public void testJacksonized$JacksonizedSuperBuilderSimple() {
    doTest(true);
  }

  public void testJacksonized$JacksonizedSuperBuilderWithJsonDeserialize() {
    doTest(true);
  }

  public void testJacksonized$JacksonBuilderSingular() {
    doTest(true);
  }

  public void testJacksonized$JacksonJsonProperty() {
    doTest(true);
  }
}
