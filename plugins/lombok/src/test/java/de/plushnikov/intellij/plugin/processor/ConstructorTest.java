package de.plushnikov.intellij.plugin.processor;

import de.plushnikov.intellij.plugin.AbstractLombokParsingTestCase;

import java.io.IOException;

/**
 * Unit tests for IntelliJPlugin for Lombok, based on lombok test classes
 */
public class ConstructorTest extends AbstractLombokParsingTestCase {

  public void testConstructors$Constructors() throws IOException {
    doTest(true);
  }

  public void testConstructors$ConflictingStaticConstructorNames() throws IOException {
    doTest(true);
  }

  public void testConstructors$NoArgsConstructorForced() throws IOException {
    doTest(true);
  }

  public void testConstructors$ConstructorEnum() throws IOException {
    doTest(true);
  }

  public void testConstructors$RequiredArgsConstructorWithGeneric136() throws IOException {
    doTest(true);
  }

  public void testConstructors$RequiredArgsConstructorWithGeneric157() throws IOException {
    doTest(true);
  }
}
