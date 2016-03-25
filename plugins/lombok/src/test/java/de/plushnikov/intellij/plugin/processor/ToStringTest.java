package de.plushnikov.intellij.plugin.processor;

import de.plushnikov.intellij.plugin.LombokParsingTestCase;

import java.io.IOException;

/**
 * Unit tests for IntelliJPlugin for Lombok, based on lombok test classes
 */
public class ToStringTest extends LombokParsingTestCase {

  public void testToStringInner() throws IOException {
    doTest();
  }

  public void testToStringPlain() throws IOException {
    doTest();
  }
}