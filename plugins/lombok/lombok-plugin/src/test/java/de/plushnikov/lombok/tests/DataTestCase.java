package de.plushnikov.lombok.tests;

import de.plushnikov.lombok.LombokParsingTestCase;
import org.junit.Test;

public class DataTestCase extends LombokParsingTestCase {
  public DataTestCase() {
  }

  @Test
  public void testDataExtended() {
    doTest();
  }

  @Test
  public void testDataIgnore() {
    doTest();
  }

  @Test
  public void testDataOnEnum() {
    doTest();
  }

  @Test
  public void testDataOnLocalClass() {
    doTest();
  }

  @Test
  public void testDataPlain() {
    doTest();
  }

  @Test
  public void testDataWithGetter() {
    doTest();
  }

  @Test
  public void testDataWithGetterNone() {
    doTest();
  }
}