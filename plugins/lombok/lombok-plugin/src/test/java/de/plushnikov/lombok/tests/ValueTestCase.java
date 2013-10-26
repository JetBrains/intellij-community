package de.plushnikov.lombok.tests;

import de.plushnikov.lombok.LombokParsingTestCase;

import java.io.IOException;

public class ValueTestCase extends LombokParsingTestCase {

  public void testValuePlain() throws IOException {
    //TODO add support for final Modifier on class
    doTest();
  }

  public void testValueExperimental() throws IOException {
    doTest();
  }

  public void testValueExperimentalStarImport() throws IOException {
    doTest();
  }
}