package de.plushnikov.intellij.plugin.processor;

import de.plushnikov.intellij.plugin.AbstractLombokParsingTestCase;

import java.io.IOException;

/**
 * Unit tests for IntelliJPlugin for Lombok, based on lombok test classes
 */
public class DataTest extends AbstractLombokParsingTestCase {

  protected boolean shouldCompareCodeBlocks() {
    return false;
  }

  public void testData$DataExtended() throws IOException {
    doTest();
  }

  public void testData$DataIgnore() throws IOException {
    doTest();
  }

  public void testData$DataOnEnum() throws IOException {
    doTest();
  }

  public void testData$DataOnLocalClass() throws IOException {
    doTest();
  }

  public void testData$DataPlain() throws IOException {
    doTest();
  }

  public void testData$DataWithGetter() throws IOException {
    doTest();
  }

  public void testData$DataWithGetterNone() throws IOException {
    doTest();
  }

  public void testData$DataStaticConstructor() throws IOException {
    // Test for issue #9
    doTest();
  }

  public void testData$DataWithGeneric176() throws IOException {
    doTest();
  }
}