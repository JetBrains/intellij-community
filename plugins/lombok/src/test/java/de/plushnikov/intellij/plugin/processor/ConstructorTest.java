package de.plushnikov.intellij.plugin.processor;

import de.plushnikov.intellij.plugin.LombokParsingTestCase;

import java.io.IOException;

/**
 * Unit tests for IntelliJPlugin for Lombok, based on lombok test classes
 */
public class ConstructorTest extends LombokParsingTestCase {

  public void testConstructors() throws IOException {
    doTest();
  }

  public void testConflictingStaticConstructorNames() throws IOException {
    doTest();
  }

  public void testNoArgsConstructorForced() throws IOException {
    doTest();
  }

  public void testConstructorEnum() throws IOException {
    doTest();
  }

  public void testRequiredArgsConstructorWithGeneric136() throws IOException {
    doTest();
  }

  public void testRequiredArgsConstructorWithGeneric157() throws IOException {
    doTest();
  }
}
