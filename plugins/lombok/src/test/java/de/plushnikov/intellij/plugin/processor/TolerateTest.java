package de.plushnikov.intellij.plugin.processor;

import de.plushnikov.intellij.plugin.LombokParsingTestCase;

import java.io.IOException;

/**
 * Unit tests for IntelliJPlugin for Lombok, based on lombok test classes
 */
public class TolerateTest extends LombokParsingTestCase {

  public void testTolerateTest() throws IOException {
    doTest();
  }
}