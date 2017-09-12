package de.plushnikov.intellij.plugin.processor;

import de.plushnikov.intellij.plugin.AbstractLombokParsingTestCase;

import java.io.IOException;

public class WitherTest extends AbstractLombokParsingTestCase {

  protected boolean shouldCompareCodeBlocks() {
    return false;
  }

  public void testWither$WitherAccessLevel() throws IOException {
    doTest(true);
  }

  public void testWither$WitherAlreadyExists() throws IOException {
    doTest(true);
  }

  public void testWither$WitherAndAccessors() throws IOException {
    doTest(true);
  }

  public void testWither$WitherAndAllArgsConstructor() throws IOException {
    doTest(true);
  }

  public void testWither$WitherDeprecated() throws IOException {
    doTest(true);
  }

  public void testWither$WitherOnClass() throws IOException {
    doTest(true);
  }

  public void testWither$WitherOnStatic() throws IOException {
    doTest(true);
  }

  public void testWither$WitherPlain() throws IOException {
    doTest(true);
  }

  public void testWither$WitherWithDollar() throws IOException {
    doTest(true);
  }

  public void testWither$WitherWithGenerics() throws IOException {
    doTest(true);
  }

  public void testWither$WitherWithAbstract() throws IOException {
    doTest(true);
  }
}
