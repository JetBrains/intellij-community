package de.plushnikov.lombok.tests;

import de.plushnikov.lombok.LombokParsingTestCase;

import java.io.IOException;

/**
 * Unit tests for IntelliJPlugin for Lombok, based on lombok test classes
 */
public class ConstructorTestCase extends LombokParsingTestCase {

  public void testConstructors() throws IOException {
    doTest();
  }

  public void testConflictingStaticConstructorNames() throws IOException {
    doTest();
  }
}
