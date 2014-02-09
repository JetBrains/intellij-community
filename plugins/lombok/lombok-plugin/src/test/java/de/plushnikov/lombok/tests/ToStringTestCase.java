package de.plushnikov.lombok.tests;

import de.plushnikov.lombok.LombokParsingTestCase;

import java.io.IOException;

/**
 * Unit tests for IntelliJPlugin for Lombok, based on lombok test classes
 * For this to work, the correct system property idea.home.path needs to be passed to the test runner.
 */
public class ToStringTestCase extends LombokParsingTestCase {

  public void testToStringInner() throws IOException {
    doTest();
  }

  public void testToStringPlain() throws IOException {
    doTest();
  }
}