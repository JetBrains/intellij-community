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
    doTest();
  }

  // This test is lombok's homepage customized example.
  public void testBuilder$BuilderExampleCustomized() throws IOException {
    doTest();
  }

  public void testBuilder$BuilderSimple() throws IOException {
    doTest();
  }

  public void testBuilder$BuilderComplex() throws IOException {
    doTest();
  }

  public void testBuilder$BuilderChainAndFluent() throws IOException {
    doTest();
  }

  public void testBuilder$BuilderWithAccessors() throws IOException {
    doTest();
  }

  // This test is lombok's homepage example with predefined elements and another inner class.
  public void testBuilder$BuilderPredefined() throws IOException {
    doTest();
  }

  public void testBuilder$BuilderWithExistingBuilderClass() throws IOException {
    doTest();
  }

  public void testBuilder$BuilderConstructorException() throws IOException {
    doTest();
  }

  public void testBuilder$BuilderAndAllArgsConstructor() throws IOException {
    doTest();
  }

  public void testBuilder$BuilderMethodException() throws IOException {
    doTest();
  }

  public void testBuilder$BuilderSingularGuavaListsSets() throws IOException {
    doTest();
  }

  public void testBuilder$BuilderSingularGuavaMaps() throws IOException {
    doTest();
  }

  public void testBuilder$BuilderSingularSets() throws IOException {
    doTest();
  }

  public void testBuilder$BuilderSingularLists() throws IOException {
    doTest();
  }

  public void testBuilder$BuilderSingularMaps() throws IOException {
    doTest();
  }

  // ignored because of disabled auto singularization
  public void ignore_testBuilder$BuilderSingularNoAuto() throws IOException {
    doTest();
  }

  // ignored because of disabled guava redirection
  public void ignore_testBuilder$BuilderSingularRedirectToGuava() throws IOException {
    doTest();
  }

  //  TODO implement me
  public void ignore_testBuilder$BuilderInstanceMethod() throws IOException {
    doTest();
  }

  public void testBuilder$BuilderSingularWithPrefixes() throws IOException {
    doTest();
  }

  //  TODO implement me
  public void ignore_testBuilder$BuilderWithToBuilder() throws IOException {
    doTest();
  }

  //  TODO fixme
  public void ignore_testBuilder$BuilderWithToBuilderOnClass() throws IOException {
    doTest();
  }

  //  TODO fixme
  public void ignore_testBuilder$BuilderWithToBuilderOnConstructor() throws IOException {
    doTest();
  }

  //  TODO fixme
  public void ignore_testBuilder$BuilderWithToBuilderOnMethod() throws IOException {
    doTest();
  }
}
