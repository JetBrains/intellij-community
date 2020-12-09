package de.plushnikov.intellij.plugin.processor;

import de.plushnikov.intellij.plugin.AbstractLombokParsingTestCase;

import java.io.IOException;

/**
 * Unit tests for IntelliJPlugin for Lombok, based on lombok test classes
 */
public class TolerateTest extends AbstractLombokParsingTestCase {

  public void testTolerateTest() throws IOException {
    doTest();
  }
}