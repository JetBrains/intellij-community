package de.plushnikov.intellij.plugin.processor;

import de.plushnikov.intellij.plugin.AbstractLombokParsingTestCase;

import java.io.IOException;

/**
 * Unit tests for IntelliJPlugin for Lombok, based on lombok test classes
 */
public class BuilderTest extends AbstractLombokParsingTestCase {
  @Override
  public void setUp() throws Exception {
    super.setUp();
    // Add dummy classes, which are absent in mockJDK
    myFixture.addClass("package java.util;\n  public interface NavigableMap<K,V> extends java.util.SortedMap<K,V> {}");
  }

  // This test is lombok's homepage example.
  public void testBuilder$BuilderExample() throws IOException {
    doTest(true);
  }

  // This test is lombok's homepage customized example.
  public void testBuilder$BuilderExampleCustomized() throws IOException {
    doTest(true);
  }

  public void testBuilder$BuilderSimple() throws IOException {
    doTest(true);
  }

  public void testBuilder$BuilderComplex() throws IOException {
    doTest(true);
  }

  public void testBuilder$BuilderChainAndFluent() throws IOException {
    doTest(true);
  }

  public void testBuilder$BuilderWithAccessors() throws IOException {
    doTest(true);
  }

  public void testBuilder$BuilderWithFieldAccessors() throws IOException {
    doTest(true);
  }

  // This test is lombok's homepage example with predefined elements and another inner class.
  public void testBuilder$BuilderPredefined() throws IOException {
    doTest(true);
  }

  public void testBuilder$BuilderWithExistingBuilderClass() throws IOException {
    doTest(true);
  }

  public void testBuilder$BuilderConstructorException() throws IOException {
    doTest(true);
  }

  public void testBuilder$BuilderAndAllArgsConstructor() throws IOException {
    doTest(true);
  }

  public void testBuilder$BuilderMethodException() throws IOException {
    doTest(true);
  }

  public void testBuilder$BuilderValueData() throws IOException {
    doTest(true);
  }

  public void testBuilder$BuilderSingularGuavaListsSets() throws IOException {
    doTest(true);
  }

  public void testBuilder$BuilderSingularGuavaMaps() throws IOException {
    doTest(true);
  }

  public void testBuilder$BuilderSingularSets() throws IOException {
    doTest(true);
  }

  public void testBuilder$BuilderSingularLists() throws IOException {
    doTest(true);
  }

  public void testBuilder$BuilderSingularMaps() throws IOException {
    doTest(true);
  }

  // ignored because of disabled auto singularization
  public void ignore_testBuilder$BuilderSingularNoAuto() throws IOException {
    doTest(true);
  }

  // ignored because of disabled guava redirection
  public void ignore_testBuilder$BuilderSingularRedirectToGuava() throws IOException {
    doTest(true);
  }

  public void testBuilder$BuilderInstanceMethod() throws IOException {
    doTest(true);
  }

  public void testBuilder$BuilderSingularWithPrefixes() throws IOException {
    doTest(true);
  }
  public void testBuilder$BuilderGenerics() throws IOException {
    doTest(true);
  }

  public void testBuilder$BuilderGenericsOnConstructor() throws IOException {
    doTest(true);
  }
}
