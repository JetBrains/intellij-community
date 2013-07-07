package de.plushnikov.lombok.tests;

import de.plushnikov.lombok.LombokParsingTestCase;
import org.junit.Test;

import java.io.IOException;

public class EqualsAndHashCodeTestCase extends LombokParsingTestCase {
  public EqualsAndHashCodeTestCase() {
  }

  @Test
  public void testEqualsAndHashCode() throws IOException {
    doTest();
  }

  @Test
  public void testEqualsAndHashCodeWithExistingMethods() throws IOException {
    doTest();
  }
}