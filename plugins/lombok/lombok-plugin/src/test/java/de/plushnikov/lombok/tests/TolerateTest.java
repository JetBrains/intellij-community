package de.plushnikov.lombok.tests;

import de.plushnikov.lombok.LombokParsingTestCase;

import java.io.IOException;

/**
 * Unit tests for IntelliJPlugin for Lombok, based on lombok test classes
 */
public class TolerateTest extends LombokParsingTestCase {

  public void testTolerateTest() throws IOException {
    doTest();
  }
}