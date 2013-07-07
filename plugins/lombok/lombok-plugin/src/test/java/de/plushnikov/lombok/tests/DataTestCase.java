package de.plushnikov.lombok.tests;

import org.junit.Test;

import de.plushnikov.lombok.LombokParsingTestCase;

import java.io.IOException;

public class DataTestCase extends LombokParsingTestCase {
  public DataTestCase() {
  }

  @Test
  public void testDataExtended() throws IOException {
    doTest();
  }

  @Test
  public void testDataIgnore() throws IOException {
    doTest();
  }

  @Test
  public void testDataOnEnum() throws IOException {
    doTest();
  }

  @Test
  public void testDataOnLocalClass() throws IOException {
    doTest();
  }

  @Test
  public void testDataPlain() throws IOException {
    doTest();
  }

  @Test
  public void testDataWithGetter() throws IOException {
    doTest();
  }

  @Test
  public void testDataWithGetterNone() throws IOException {
    doTest();
  }

  @Test
  public void testDataStaticConstructor() throws IOException {
    // Test for issue #9
    doTest();
  }
}