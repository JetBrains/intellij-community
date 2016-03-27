package de.plushnikov.intellij.plugin.processor;

import de.plushnikov.intellij.plugin.AbstractLombokParsingTestCase;

import java.io.IOException;

/**
 * Unit tests for IntelliJPlugin for Lombok, based on lombok test classes
 */
public class SetterTest extends AbstractLombokParsingTestCase {

  public void testSetterAccessLevel() throws IOException {
    doTest();
  }

  public void testSetterAlreadyExists() throws IOException {
    doTest();
  }

  public void testSetterDeprecated() throws IOException {
    doTest();
  }

  public void testSetterOnClass() throws IOException {
    doTest();
  }

  public void testSetterOnMethodOnParam() throws IOException {
    doTest();
  }

  public void testSetterOnStatic() throws IOException {
    doTest();
  }

  public void testSetterPlain() throws IOException {
    doTest();
  }

  public void testSetterWithDollar() throws IOException {
    doTest();
  }
}