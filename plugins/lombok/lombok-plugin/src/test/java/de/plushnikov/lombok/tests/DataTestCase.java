package de.plushnikov.lombok.tests;

import org.junit.Test;

import de.plushnikov.lombok.LombokParsingTestCase;

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

  @Test
  public void testDataStaticConstructor() {
    // Test for issue #9
    doTest();
  }
}