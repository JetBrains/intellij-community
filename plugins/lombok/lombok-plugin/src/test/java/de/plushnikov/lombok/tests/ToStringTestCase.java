package de.plushnikov.lombok.tests;

import de.plushnikov.lombok.LombokParsingTestCase;
import org.junit.Test;

import java.io.IOException;

public class ToStringTestCase extends LombokParsingTestCase {
  public ToStringTestCase() {
  }

  @Test
  public void testToStringInner() throws IOException {
    doTest();
  }

  @Test
  public void testToStringPlain() throws IOException {
    doTest();
  }
}