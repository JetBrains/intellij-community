package de.plushnikov.intellij.plugin.processor;

import de.plushnikov.intellij.plugin.AbstractLombokParsingTestCase;

import java.io.IOException;

/**
 * Unit tests for IntelliJPlugin for Lombok, based on lombok test classes
 */
public class ConstructorTest extends AbstractLombokParsingTestCase {

  public void testConstructors$Constructors() {
    doTest(true);
  }

  public void testConstructors$ConflictingStaticConstructorNames() {
    doTest(true);
  }

  public void testConstructors$NoArgsConstructorForced() {
    doTest(true);
  }

  public void testConstructors$ConstructorEnum() {
    doTest(true);
  }

  public void testConstructors$RequiredArgsConstructorWithGeneric136() {
    doTest(true);
  }

  public void testConstructors$RequiredArgsConstructorWithGeneric157() {
    doTest(true);
  }
}
