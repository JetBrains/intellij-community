package de.plushnikov.lombok.tests;

import de.plushnikov.lombok.LombokParsingTestCase;
import java.io.IOException;

public class ValueTestCase extends LombokParsingTestCase {

  public void testValuePlain() throws IOException {
    doTest();
  }

  public void testValueExperimental() throws IOException {
    doTest();
  }

  public void testValueExperimentalStarImport() throws IOException {
    doTest();
  }
}