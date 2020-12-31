package de.plushnikov.intellij.plugin.processor;

import de.plushnikov.intellij.plugin.AbstractLombokParsingTestCase;

public class WithTest extends AbstractLombokParsingTestCase {

  @Override
  protected boolean shouldCompareCodeBlocks() {
    return false;
  }

  public void testWith$WithAlreadyExists() {
    doTest(true);
  }

  public void testWith$WithAndAllArgsConstructor() {
    doTest(true);
  }

  public void testWith$WitherAccessLevel() {
    doTest(true);
  }

  public void testWith$WitherLegacyStar() {
    doTest(true);
  }

  public void testWith$WithInnerAnnotation() {
    doTest(true);
  }

  public void testWith$WithMethodAbstract() {
    doTest(true);
  }

  public void testWith$WithMethodMarkedDeprecated() {
    doTest(true);
  }

  public void testWith$WithOnClass() {
    doTest(true);
  }

  public void testWith$WithOnStatic() {
    doTest(true);
  }

  public void testWith$WithPlain() {
    doTest(true);
  }

  public void testWith$WithWithDollar() {
    doTest(true);
  }

  public void testWith$WithWithGenerics() {
    doTest(true);
  }

  public void testWith$WithWithTypeAnnos() {
    doTest(true);
  }

  public void testWith$WithAndBuilderDefaultOnFieldAndValueOnClass() {
    doTest(true);
  }

}
