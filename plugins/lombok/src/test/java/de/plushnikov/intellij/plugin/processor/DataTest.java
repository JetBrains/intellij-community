package de.plushnikov.intellij.plugin.processor;

import de.plushnikov.intellij.plugin.AbstractLombokParsingTestCase;

/**
 * Unit tests for IntelliJPlugin for Lombok, based on lombok test classes
 */
public class DataTest extends AbstractLombokParsingTestCase {

  @Override
  protected boolean shouldCompareCodeBlocks() {
    return false;
  }

  public void testData$DataExtended() {
    doTest(true);
  }

  public void testData$DataIgnore() {
    doTest(true);
  }

  public void testData$DataOnEnum() {
    doTest(true);
  }

  public void testData$DataOnLocalClass() {
    doTest(true);
  }

  public void testData$DataPlain() {
    doTest(true);
  }

  public void testData$DataWithGetter() {
    doTest(true);
  }

  public void testData$DataWithGetterNone() {
    doTest(true);
  }

  public void testData$DataStaticConstructor() {
    // Test for issue #9
    doTest(true);
  }

  public void testData$DataStaticConstructor2() {
    doTest(true);
  }

  public void testData$DataStaticConstructor3() {
    doTest(true);
  }

  public void testData$DataWithGeneric176() {
    doTest(true);
  }

  public void testData$Klasse663() {
    doTest(true);
  }

  public void testData$DataAndBuilder() {
    doTest(true);
  }

  public void testData$DataAndSuperBuilder() {
    doTest(true);
  }
}
