package de.plushnikov.lombok.tests;

import de.plushnikov.lombok.LombokParsingTestCase;
import org.junit.Test;

public class EqualsAndHashCodeTestCase extends LombokParsingTestCase {
  public EqualsAndHashCodeTestCase() {
  }

  @Test
  public void testEqualsAndHashCode() {
    doTest();
  }

  @Test
  public void testEqualsAndHashCodeWithExistingMethods() {
    doTest();
  }
}