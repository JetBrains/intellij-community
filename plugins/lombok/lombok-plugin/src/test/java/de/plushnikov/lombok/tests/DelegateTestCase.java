package de.plushnikov.lombok.tests;

import de.plushnikov.lombok.LombokParsingTestCase;
import org.junit.Test;

public class DelegateTestCase extends LombokParsingTestCase {
  public DelegateTestCase() {
  }

  @Test
  public void testDelegateTypesAndExcludes() {
    doTest();
  }
}