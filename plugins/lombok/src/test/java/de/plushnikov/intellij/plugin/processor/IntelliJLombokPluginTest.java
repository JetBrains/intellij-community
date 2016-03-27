package de.plushnikov.intellij.plugin.processor;

import de.plushnikov.intellij.plugin.AbstractLombokParsingTestCase;

import java.io.IOException;

/**
 * Unit tests for IntelliJPlugin for Lombok, based on lombok test classes
 */
public class IntelliJLombokPluginTest extends AbstractLombokParsingTestCase {

  protected boolean shouldCompareCodeBlocks() {
    return false;
  }

  public void testNonNullPlain() throws IOException {
    doTest();
  }

  public void testSynchronizedName() throws IOException {
    doTest();
  }

  public void ignore_testSynchronizedPlain() throws IOException {
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
