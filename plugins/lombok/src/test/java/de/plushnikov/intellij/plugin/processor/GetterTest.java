package de.plushnikov.intellij.plugin.processor;

import de.plushnikov.intellij.plugin.AbstractLombokParsingTestCase;

import java.io.IOException;

/**
 * Unit tests for IntelliJPlugin for Lombok, based on lombok test classes
 */
public class GetterTest extends AbstractLombokParsingTestCase {

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

  public void ignore_testGetterLazy() throws IOException {
    //TODO known problem, try to fix later
    doTest();
  }

  public void ignore_testGetterLazyBoolean() throws IOException {
    //TODO known problem, try to fix later
    doTest();
  }

  public void ignore_testGetterLazyEahcToString() throws IOException {
    //TODO known problem, try to fix later
    doTest();
  }

  public void testGetterLazyInvalid() throws IOException {
    doTest();
  }

  public void ignore_testGetterLazyNative() throws IOException {
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