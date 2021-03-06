package de.plushnikov.intellij.plugin.processor;

import de.plushnikov.intellij.plugin.AbstractLombokParsingTestCase;

public class ValueTest extends AbstractLombokParsingTestCase {

  @Override
  protected boolean shouldCompareCodeBlocks() {
    return false;
  }

  public void testValue$ValueIssue78() {
    doTest(true);
  }

  public void testValue$ValueIssue94() {
    doTest(true);
  }

  public void testValue$ValuePlain() {
    doTest(true);
  }

  public void testValue$ValueStarImport() {
    doTest(true);
  }

  public void testValue$ValueStaticConstructor() {
    doTest(true);
  }

  public void testValue$ValueBuilder() {
    doTest(true);
  }

  public void testValue$ValueAndBuilder93() {
    doTest(true);
  }

  public void testValue$ValueAndWither() {
    doTest(true);
  }

  public void testValue$ValueAndWitherAndRequiredConstructor() {
    doTest(true);
  }

  public void testValue$ValueWithGeneric176() {
    doTest(true);
  }

  public void testValue$ValueWithPackagePrivate() {
    doTest(true);
  }

  public void testValue$ValueWithNonDefaultConstructor() {
    doTest(true);
  }
}
