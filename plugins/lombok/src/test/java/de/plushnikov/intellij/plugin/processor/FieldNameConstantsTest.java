package de.plushnikov.intellij.plugin.processor;

import de.plushnikov.intellij.plugin.AbstractLombokParsingTestCase;

import java.io.IOException;

/**
 * Unit tests for IntelliJPlugin for Lombok, based on lombok test classes
 */
public class FieldNameConstantsTest extends AbstractLombokParsingTestCase {

  public void testFieldNameConstants$FieldNameConstantsBasic() throws IOException {
    doTest(true);
  }

  public void testFieldNameConstants$FieldNameConstantsWeird() throws IOException {
    doTest(true);
  }

}
