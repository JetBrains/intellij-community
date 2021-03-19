package de.plushnikov.intellij.plugin.processor;

import de.plushnikov.intellij.plugin.AbstractLombokParsingTestCase;

/**
 * Unit tests for IntelliJPlugin for Lombok, based on lombok test classes
 */
public class TolerateTest extends AbstractLombokParsingTestCase {

  public void testTolerate$TolerateTest() {
    doTest(true);
  }

  public void testTolerate$WitherTolerateTest() {
    doTest(true);
  }
}
