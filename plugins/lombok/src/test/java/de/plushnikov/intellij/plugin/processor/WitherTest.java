package de.plushnikov.intellij.plugin.processor;

import de.plushnikov.intellij.plugin.AbstractLombokParsingTestCase;

import java.io.IOException;

public class WitherTest extends AbstractLombokParsingTestCase {

  protected boolean shouldCompareCodeBlocks() {
    return false;
  }

  public void testWither$WitherAccessLevel() throws IOException {
    doTest();
  }

  public void testWither$WitherAlreadyExists() throws IOException {
    doTest();
  }

  public void testWither$WitherAndAllArgsConstructor() throws IOException {
    doTest();
  }

  public void testWither$WitherDeprecated() throws IOException {
    doTest();
  }

  public void testWither$WitherOnClass() throws IOException {
    doTest();
  }

  public void testWither$WitherOnStatic() throws IOException {
    doTest();
  }

  public void testWither$WitherPlain() throws IOException {
    doTest();
  }

  public void testWither$WitherWithDollar() throws IOException {
    doTest();
  }

  public void testWither$WitherWithGenerics() throws IOException {
    doTest();
  }
}