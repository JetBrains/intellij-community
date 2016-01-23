package de.plushnikov.lombok.tests;

import de.plushnikov.lombok.LombokParsingTestCase;

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