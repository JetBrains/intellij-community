package de.plushnikov.lombok.tests;

import de.plushnikov.lombok.LombokParsingTestCase;
import org.junit.Test;

public class SetterTestCase extends LombokParsingTestCase {
  public SetterTestCase() {
  }

  @Test
  public void testSetterAccessLevel() {
    doTest();
  }

  @Test
  public void testSetterAlreadyExists() {
    doTest();
  }

  @Test
  public void testSetterOnClass() {
    doTest();
  }

  @Test
  public void testSetterOnStatic() {
    doTest();
  }

  @Test
  public void testSetterPlain() {
    doTest();
  }

  @Test
  public void testSetterWithDollar() {
    doTest();
  }
}