package de.plushnikov.intellij.plugin.processor;

import de.plushnikov.intellij.plugin.AbstractLombokParsingTestCase;

import java.io.IOException;

public class UtilityClassTest extends AbstractLombokParsingTestCase {

  public void testUtilityclass$UtilityClassPlain() {
    doTest(true);
  }
}
