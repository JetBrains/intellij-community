package de.plushnikov.intellij.plugin.processor;

import java.io.IOException;

import de.plushnikov.intellij.plugin.AbstractLombokParsingTestCase;

public class UtilityClassTest extends AbstractLombokParsingTestCase {

  public void testUtilityclass$UtilityClassPlain() throws IOException {
    doTest(true);
  }
}
