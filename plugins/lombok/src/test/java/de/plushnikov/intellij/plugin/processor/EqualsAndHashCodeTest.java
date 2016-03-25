package de.plushnikov.intellij.plugin.processor;

import de.plushnikov.intellij.plugin.LombokParsingTestCase;

/**
 * Unit tests for IntelliJPlugin for Lombok, based on lombok test classes
 */
public class EqualsAndHashCodeTest extends LombokParsingTestCase {

  public void testEqualsAndHashCode() throws Exception {
    doTest();
  }

  public void testEqualsAndHashCodeWithExistingMethods() throws Exception {
    doTest();
  }

  public void testEqualsAndHashCodeWithSomeExistingMethods() throws Exception {
    doTest();
  }
}