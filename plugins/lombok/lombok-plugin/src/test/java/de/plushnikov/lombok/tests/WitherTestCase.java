package de.plushnikov.lombok.tests;

import de.plushnikov.lombok.LombokParsingTestCase;

import java.io.IOException;

public class WitherTestCase extends LombokParsingTestCase {

  protected boolean shouldCompareCodeBlocks() {
    return false;
  }

  public void testWitherAccessLevel() throws IOException {
    doTest();
  }

  public void testWitherAlreadyExists() throws IOException {
    doTest();
  }

  public void testWitherAndAllArgsConstructor() throws IOException {
    doTest();
  }

  public void testWitherDeprecated() throws IOException {
    doTest();
  }

  public void testWitherOnClass() throws IOException {
    doTest();
  }

  public void testWitherOnStatic() throws IOException {
    doTest();
  }

  public void testWitherPlain() throws IOException {
    doTest();
  }

  public void testWitherWithDollar() throws IOException {
    doTest();
  }

  public void testWitherWithGenerics() throws IOException {
    doTest();
  }
}