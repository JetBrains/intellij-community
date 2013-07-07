package de.plushnikov.lombok.tests;

import de.plushnikov.lombok.LombokParsingTestCase;
import org.junit.Test;

import java.io.IOException;

public class DelegateTestCase extends LombokParsingTestCase {
  public DelegateTestCase() {
  }

  @Test
  public void testDelegateTypesAndExcludes() throws IOException {
    doTest();
  }
}