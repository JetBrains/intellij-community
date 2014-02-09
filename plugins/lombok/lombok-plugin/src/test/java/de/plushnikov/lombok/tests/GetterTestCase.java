package de.plushnikov.lombok.tests;

import de.plushnikov.lombok.LombokParsingTestCase;

import java.io.IOException;

/**
 * Unit tests for IntelliJPlugin for Lombok, based on lombok test classes
 * For this to work, the correct system property idea.home.path needs to be passed to the test runner.
 */
public class GetterTestCase extends LombokParsingTestCase {

  protected boolean shouldCompareCodeBlocks() {
    return false;
  }

  public void testGetterAccessLevel() throws IOException {
    doTest();
  }

  public void testGetterAlreadyExists() throws IOException {
    doTest();
  }

  public void testGetterBoolean() throws IOException {
    doTest();
  }

  public void testGetterDeprecated() throws IOException {
    doTest();
  }

  public void testGetterEnum() throws IOException {
    doTest();
  }

  public void testGetterLazy() throws IOException {
    //TODO known problem, try to fix later
    doTest();
  }

  public void testGetterLazyBoolean() throws IOException {
    //TODO known problem, try to fix later
    doTest();
  }

  public void testGetterLazyEahcToString() throws IOException {
    //TODO known problem, try to fix later
    doTest();
  }

  public void testGetterLazyInvalid() throws IOException {
    doTest();
  }

  public void testGetterLazyNative() throws IOException {
    //TODO known problem, try to fix later
    doTest();
  }

  public void testGetterNone() throws IOException {
    doTest();
  }

  public void testGetterOnClass() throws IOException {
    doTest();
  }

  public void testGetterOnMethod() throws IOException {
    doTest();
  }

  public void testGetterOnMethodErrors() throws IOException {
    doTest();
  }

  public void testGetterOnMethodErrors2() throws IOException {
    doTest();
  }

  public void testGetterOnStatic() throws IOException {
    doTest();
  }

  public void testGetterPlain() throws IOException {
    doTest();
  }

  public void testGetterWithDollar() throws IOException {
    doTest();
  }

  public void testMultiFieldGetter() throws IOException {
    doTest();
  }

  public void testTrickyTypeResolution() throws IOException {
    doTest();
  }

  public void testClassNamedAfterGetter() throws IOException {
    doTest();
  }

  public void testCommentsInterspersed() throws IOException {
    doTest();
  }
}