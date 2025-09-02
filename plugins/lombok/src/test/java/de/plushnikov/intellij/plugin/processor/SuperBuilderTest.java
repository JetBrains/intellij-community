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

  public void testSuperbuilder$SuperBuilderAbstract() {
    doTest(true);
  }

  public void testSuperbuilder$SuperBuilderAbstractToBuilder() {
    doTest(true);
  }

  public void testSuperbuilder$SuperBuilderBasic() {
    doTest(true);
  }

  public void testSuperbuilder$SuperBuilderBasicToBuilder() {
    doTest(true);
  }

  public void testSuperbuilder$SuperBuilderCustomized() {
    doTest(true);
  }

  public void testSuperbuilder$SuperBuilderToBuilderOverride() {
    doTest(true);
  }

  //TODO Implement AnnotatedTypes handling/delombok
//  public void testSuperbuilder$SuperBuilderSingularAnnotatedTypes() {
//    doTest(true);
//  }

  public void testSuperbuilder$SuperBuilderWithCustomBuilderMethod() {
    doTest(true);
  }

// TODO Implement defaults handling/delombok
//  public void testSuperbuilder$SuperBuilderWithDefaults() {
//    doTest(true);
//  }

  public void testSuperbuilder$SuperBuilderWithGenerics() {
    doTest(true);
  }

  public void testSuperbuilder$SuperBuilderWithGenerics2() {
    doTest(true);
  }

  public void testSuperbuilder$SuperBuilderWithGenericsAndToBuilder() {
    doTest(true);
  }

  // TODO Implement NonNull handling/delombok
//  public void testSuperbuilder$SuperBuilderWithNonNull() {
//    doTest(true);
//  }

  public void testSuperbuilder$SuperBuilderWithSupportClass() {
    doTest(true);
  }

}
