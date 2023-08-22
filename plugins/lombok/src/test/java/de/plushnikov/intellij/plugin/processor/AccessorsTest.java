package de.plushnikov.intellij.plugin.processor;

import de.plushnikov.intellij.plugin.AbstractLombokParsingTestCase;

/**
 * Unit tests for IntelliJPlugin for Lombok, based on lombok test classes
 */
public class AccessorsTest extends AbstractLombokParsingTestCase {

  public void testAccessors$Accessors() {
    doTest(true);
  }

  public void testAccessors$AccessorsCascade() {
    doTest(true);
  }

  public void testAccessors$AccessorsMakeFinal() {
    doTest(true);
  }
}
