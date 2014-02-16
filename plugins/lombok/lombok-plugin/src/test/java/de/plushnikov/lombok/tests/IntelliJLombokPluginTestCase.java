package de.plushnikov.lombok.tests;

import de.plushnikov.lombok.LombokParsingTestCase;

import java.io.IOException;

/**
 * Unit tests for IntelliJPlugin for Lombok, based on lombok test classes
 */
public class IntelliJLombokPluginTestCase extends LombokParsingTestCase {

  protected boolean shouldCompareCodeBlocks() {
    return false;
  }

  public void testNonNullPlain() throws IOException {
    doTest();
  }

  public void testSynchronizedName() throws IOException {
    doTest();
  }

  public void testSynchronizedPlain() throws IOException {
    //TODO known problem, try to fix later
    doTest();
  }

  public void testClassNamedAfterGetter() throws IOException {
    doTest();
  }

  public void testCommentsInterspersed() throws IOException {
    doTest();
  }

}
