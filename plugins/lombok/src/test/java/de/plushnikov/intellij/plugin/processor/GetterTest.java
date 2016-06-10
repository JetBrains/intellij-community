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

  public void testGetter$GetterAccessLevel() throws IOException {
    doTest();
  }

  public void testGetter$GetterAlreadyExists() throws IOException {
    doTest();
  }

  public void testGetter$GetterBoolean() throws IOException {
    doTest();
  }

  public void testGetter$GetterDeprecated() throws IOException {
    doTest();
  }

  public void testGetter$GetterEnum() throws IOException {
    doTest();
  }

  public void ignore_testGetter$GetterLazy() throws IOException {
    //TODO known problem, try to fix later
    doTest();
  }

  public void ignore_testGetter$GetterLazyBoolean() throws IOException {
    //TODO known problem, try to fix later
    doTest();
  }

  public void ignore_testGetter$GetterLazyEahcToString() throws IOException {
    //TODO known problem, try to fix later
    doTest();
  }

  public void testGetter$GetterLazyInvalid() throws IOException {
    doTest();
  }

  public void ignore_testGetter$GetterLazyNative() throws IOException {
    //TODO known problem, try to fix later
    doTest();
  }

  public void testGetter$GetterNone() throws IOException {
    doTest();
  }

  public void testGetter$GetterOnClass() throws IOException {
    doTest();
  }

  public void testGetter$GetterOnMethod() throws IOException {
    doTest();
  }

  public void testGetter$GetterOnMethodErrors() throws IOException {
    doTest();
  }

  public void testGetter$GetterOnMethodErrors2() throws IOException {
    doTest();
  }

  public void testGetter$GetterOnStatic() throws IOException {
    doTest();
  }

  public void testGetter$GetterPlain() throws IOException {
    doTest();
  }

  public void testGetter$GetterWithDollar() throws IOException {
    doTest();
  }

  public void testGetter$MultiFieldGetter() throws IOException {
    doTest();
  }

  public void testGetter$TrickyTypeResolution() throws IOException {
    doTest();
  }

  public void testGetter$ClassNamedAfterGetter() throws IOException {
    doTest();
  }

  public void testGetter$CommentsInterspersed() throws IOException {
    doTest();
  }
}