package de.plushnikov.intellij.plugin.processor;

import de.plushnikov.intellij.plugin.AbstractLombokParsingTestCase;

/**
 * Unit tests for IntelliJPlugin for Lombok, based on lombok test classes
 */
public class EqualsAndHashCodeTest extends AbstractLombokParsingTestCase {

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