package de.plushnikov.intellij.plugin.processor;

import com.intellij.openapi.util.RecursionManager;
import de.plushnikov.intellij.plugin.AbstractLombokParsingTestCase;

/**
 * Unit tests for IntelliJPlugin for Lombok, based on lombok test classes
 */
public class BuilderTest extends AbstractLombokParsingTestCase {

  @Override
  protected String annotationToComparePattern() {
    return "java.lang.Deprecated";
  }

  @Override
  public void setUp() throws Exception {
    super.setUp();
    // Add dummy classes, which are absent in mockJDK
    myFixture.addClass("package java.util;\n  public interface NavigableMap<K,V> extends java.util.SortedMap<K,V> {}");

    //TODO disable assertions for the moment
    RecursionManager.disableMissedCacheAssertions(myFixture.getProjectDisposable());
  }

  // This test is lombok's homepage example.
  public void testBuilder$BuilderExample() {
    doTest(true);
  }

  // This test is lombok's homepage customized example.
  public void testBuilder$BuilderExampleCustomized() {
    doTest(true);
  }

  public void testBuilder$BuilderSimple() {
    doTest(true);
  }

  public void testBuilder$BuilderComplex() {
    doTest(true);
  }

  public void testBuilder$BuilderWithAccessors() {
    doTest(true);
  }

  public void testBuilder$BuilderWithAccessorsWithSetterPrefix() {
    doTest(true);
  }

  public void testBuilder$BuilderWithFieldAccessors() {
    doTest(true);
  }

  // This test is lombok's homepage example with predefined elements and another inner class.
  public void testBuilder$BuilderPredefined() {
    doTest(true);
  }

  public void testBuilder$BuilderWithExistingBuilderClass() {
    doTest(true);
  }

  public void testBuilder$BuilderConstructorException() {
    doTest(true);
  }

  public void testBuilder$BuilderMultipleConstructorException() {
    doTest(true);
  }

  public void testBuilder$BuilderAndAllArgsConstructor() {
    doTest(true);
  }

  public void testBuilder$BuilderMethodException() {
    doTest(true);
  }

  public void testBuilder$BuilderValueData() {
    doTest(true);
  }

  public void testBuilder$BuilderSingularGuavaListsSets() {
    doTest(true);
  }

  public void testBuilder$BuilderSingularGuavaMaps() {
    doTest(true);
  }

  public void testBuilder$BuilderSingularSets() {
    doTest(true);
  }

  public void testBuilder$BuilderSingularSetsWithSetterPrefix() {
    doTest(true);
  }

  public void testBuilder$BuilderSingularLists() {
    doTest(true);
  }

  public void testBuilder$BuilderSingularMaps() {
    doTest(true);
  }

  // ignored because of disabled auto singularization
  public void ignore_testBuilder$BuilderSingularNoAuto() {
    doTest(true);
  }

  // ignored because of disabled guava redirection
  public void ignore_testBuilder$BuilderSingularRedirectToGuava() {
    doTest(true);
  }

  public void testBuilder$BuilderInstanceMethod() {
    doTest(true);
  }

  public void testBuilder$BuilderSingularWithPrefixes() {
    doTest(true);
  }

  public void testBuilder$ObjectApiResponse() {
    doTest(true);
  }

  public void testBuilder$BuilderGenericsOnConstructor() {
    doTest(true);
  }

  public void testBuilder$BuilderWithDeprecatedField() {
    doTest(true);
  }

  public void testBuilder$BuilderWithDeprecatedParam() {
    doTest(true);
  }

  public void testBuilder$BuilderWithNoBuilderMethod() {
    doTest(true);
  }

  public void testBuilder$BuilderSimpleProtected() {
    doTest(true);
  }

  public void testBuilder$BuilderWithTolerate() {
    doTest(true);
  }
}
