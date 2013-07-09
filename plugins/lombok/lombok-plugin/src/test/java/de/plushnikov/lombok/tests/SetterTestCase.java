package de.plushnikov.lombok.tests;

import de.plushnikov.lombok.LombokParsingTestCase;
import org.junit.Test;

import java.io.IOException;

public class SetterTestCase extends LombokParsingTestCase {

  @Test
  public void testSetterAccessLevel() throws IOException {
    doTest();
  }

  @Test
  public void testSetterAlreadyExists() throws IOException {
    doTest();
  }

  @Test
  public void testSetterDeprecated() throws IOException {
    doTest();
  }

  @Test
  public void testSetterOnClass() throws IOException {
    doTest();
  }

  @Test
  public void testSetterOnMethodOnParam() throws IOException {
    doTest();
  }

  @Test
  public void testSetterOnStatic() throws IOException {
    doTest();
  }

  @Test
  public void testSetterPlain() throws IOException {
    doTest();
  }

  @Test
  public void testSetterWithDollar() throws IOException {
    doTest();
  }
}