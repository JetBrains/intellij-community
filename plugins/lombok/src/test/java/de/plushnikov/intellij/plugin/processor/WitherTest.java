package de.plushnikov.intellij.plugin.processor;

import de.plushnikov.intellij.plugin.AbstractLombokParsingTestCase;

public class WitherTest extends AbstractLombokParsingTestCase {

  @Override
  protected boolean shouldCompareCodeBlocks() {
    return false;
  }

  public void testWither$WitherAccessLevel() {
    doTest(true);
  }

  public void testWither$WitherAlreadyExists() {
    doTest(true);
  }

  public void testWither$WitherAndAccessors() {
    doTest(true);
  }

  public void testWither$WitherAndAllArgsConstructor() {
    doTest(true);
  }

  public void testWither$WitherDeprecated() {
    doTest(true);
  }

  public void testWither$WitherOnClass() {
    doTest(true);
  }

  public void testWither$WitherOnStatic() {
    doTest(true);
  }

  public void testWither$WitherPlain() {
    doTest(true);
  }

  public void testWither$WitherWithDollar() {
    doTest(true);
  }

  public void testWither$WitherWithGenerics() {
    doTest(true);
  }

  public void testWither$WitherWithAbstract() {
    doTest(true);
  }
}
