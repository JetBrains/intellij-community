package de.plushnikov.lombok.tests;

import de.plushnikov.lombok.LombokParsingTestCase;

import java.io.IOException;

public class ValueTest extends LombokParsingTestCase {
  protected boolean shouldCompareModifiers() {
    return false;
  }

  protected boolean shouldCompareCodeBlocks() {
    return false;
  }

  public void testValueIssue78() throws IOException {
    //TODO After adding support for final Modifier on class/fields -> adapt test
    doTest();
  }

  public void testValueIssue94() throws IOException {
    //TODO After adding support for final Modifier on class/fields -> adapt test
    doTest();
  }

  public void testValuePlain() throws IOException {
    //TODO add support for final Modifier on class
    doTest();
  }

  public void testValueExperimental() throws IOException {
    doTest();
  }

  public void testValueExperimentalStarImport() throws IOException {
    doTest();
  }

  public void testValueBuilder() throws IOException {
    doTest();
  }

  public void testValueAndBuilder93() throws IOException {
    doTest();
  }

  public void testValueAndWither() throws IOException {
    doTest();
  }

  public void testValueAndWitherAndRequiredConstructor() throws IOException {
    doTest();
  }

  public void testValueWithGeneric176() throws IOException {
    doTest();
  }
}