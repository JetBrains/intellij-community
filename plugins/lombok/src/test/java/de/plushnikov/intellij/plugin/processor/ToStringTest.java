package de.plushnikov.intellij.plugin.processor;

import de.plushnikov.intellij.plugin.AbstractLombokParsingTestCase;

import java.io.IOException;

/**
 * Unit tests for IntelliJPlugin for Lombok, based on lombok test classes
 */
public class ToStringTest extends AbstractLombokParsingTestCase {

  public void testTostring$ToStringInner() throws IOException {
    doTest(true);
  }

  public void testTostring$ToStringPlain() throws IOException {
    doTest(true);
  }

  public void testTostring$ToStringExplicitEmptyOf() throws Exception {
    doTest(true);
  }

  public void testTostring$ToStringExplicitOfAndExclude() throws Exception {
    doTest(true);
  }

  public void testTostring$ToStringSimpleClassName() throws IOException {
    doTest(true);
  }
}
