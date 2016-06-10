package de.plushnikov.intellij.plugin.processor;

import de.plushnikov.intellij.plugin.AbstractLombokParsingTestCase;

import java.io.IOException;

/**
 * Unit tests for IntelliJPlugin for Lombok, based on lombok test classes
 */
public class ConstructorTest extends AbstractLombokParsingTestCase {

  public void testConstructors$Constructors() throws IOException {
    doTest();
  }

  public void testConstructors$ConflictingStaticConstructorNames() throws IOException {
    doTest();
  }

  public void testConstructors$NoArgsConstructorForced() throws IOException {
    doTest();
  }

  public void testConstructors$ConstructorEnum() throws IOException {
    doTest();
  }

  public void testConstructors$RequiredArgsConstructorWithGeneric136() throws IOException {
    doTest();
  }

  public void testConstructors$RequiredArgsConstructorWithGeneric157() throws IOException {
    doTest();
  }
}
