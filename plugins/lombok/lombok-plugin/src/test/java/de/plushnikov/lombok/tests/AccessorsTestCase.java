package de.plushnikov.lombok.tests;

import de.plushnikov.lombok.LombokParsingTestCase;

/**
 * Unit tests for IntelliJPlugin for Lombok, based on lombok test classes
 * For this to work, the correct system property idea.home.path needs to be passed to the test runner.
 */
public class AccessorsTestCase extends LombokParsingTestCase {

  public void testAccessors() throws Exception {
    doTest();
  }
}