package de.plushnikov.lombok.tests;

import de.plushnikov.lombok.LombokParsingTestCase;
import org.junit.Test;

public class ToStringTestCase extends LombokParsingTestCase {
  public ToStringTestCase() {
  }

  @Test
  public void testToStringInner() {
    doTest();
  }

  @Test
  public void testToStringPlain() {
    doTest();
  }
}