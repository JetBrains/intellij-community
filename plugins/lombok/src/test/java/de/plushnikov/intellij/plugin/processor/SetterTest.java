package de.plushnikov.intellij.plugin.processor;

import de.plushnikov.intellij.plugin.AbstractLombokParsingTestCase;

import java.io.IOException;

/**
 * Unit tests for IntelliJPlugin for Lombok, based on lombok test classes
 */
public class SetterTest extends AbstractLombokParsingTestCase {

  public void testSetter$SetterAccessLevel() throws IOException {
    doTest(true);
  }

  public void testSetter$SetterAlreadyExists() throws IOException {
    doTest(true);
  }

  public void testSetter$SetterDeprecated() throws IOException {
    doTest(true);
  }

  public void testSetter$SetterOnClass() throws IOException {
    doTest(true);
  }

  public void testSetter$SetterOnMethodOnParam() throws IOException {
    doTest(true);
  }

  public void testSetter$SetterOnStatic() throws IOException {
    doTest(true);
  }

  public void testSetter$SetterPlain() throws IOException {
    doTest(true);
  }

  public void testSetter$SetterWithDollar() throws IOException {
    doTest(true);
  }
}