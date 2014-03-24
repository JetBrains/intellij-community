package de.plushnikov.lombok.tests;

import de.plushnikov.lombok.LombokParsingTestCase;

import java.io.IOException;

/**
 * Unit tests for IntelliJPlugin for Lombok, based on lombok test classes
 */
public class BuilderTestCase extends LombokParsingTestCase {

  protected boolean shouldCompareCodeBlocks() {
    return false;
  }

  // This test is lombok's homepage example.
  public void testBuilderExample() throws IOException {
    doTest();
  }

  // This test is lombok's homepage customized example.
  public void testBuilderExampleCustomized() throws IOException {
    doTest();
  }

  // This test is lombok's homepage example with predefined elements and another inner class.
  // TODO support Predefined inner builder class
  public void testBuilderPredefined() throws IOException {
    doTest();
  }

  public void testBuilderSimple() throws IOException {
    doTest();
  }

  public void testBuilderComplex() throws IOException {
    doTest();
  }

  // TODO support Predefined inner builder class
  public void testBuilderWithExistingBuilderClass() throws IOException {
    doTest();
  }
}
