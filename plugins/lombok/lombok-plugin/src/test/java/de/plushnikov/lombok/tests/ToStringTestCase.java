package de.plushnikov.lombok.tests;

import de.plushnikov.lombok.LombokParsingTestCase;

import java.io.IOException;

/**
 * Unit tests for IntelliJPlugin for Lombok, based on lombok test classes
 */
public class ToStringTestCase extends LombokParsingTestCase {

  public void testToStringInner() throws IOException {
    doTest();
  }

  public void testToStringPlain() throws IOException {
    doTest();
  }
}