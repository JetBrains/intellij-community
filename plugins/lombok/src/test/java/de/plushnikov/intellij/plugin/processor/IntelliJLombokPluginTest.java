package de.plushnikov.intellij.plugin.processor;

import de.plushnikov.intellij.plugin.AbstractLombokParsingTestCase;

/**
 * Unit tests for IntelliJPlugin for Lombok, based on lombok test classes
 */
public class IntelliJLombokPluginTest extends AbstractLombokParsingTestCase {

  @Override
  protected boolean shouldCompareCodeBlocks() {
    return false;
  }

  public void testNonNullPlain() {
    doTest();
  }

  public void testSynchronizedName() {
    doTest();
  }

  public void ignore_testSynchronizedPlain() {
    //TODO known problem, try to fix later
    doTest();
  }

}
