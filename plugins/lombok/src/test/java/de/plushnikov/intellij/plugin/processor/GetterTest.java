package de.plushnikov.intellij.plugin.processor;

import de.plushnikov.intellij.plugin.AbstractLombokParsingTestCase;

/**
 * Unit tests for IntelliJPlugin for Lombok, based on lombok test classes
 */
public class GetterTest extends AbstractLombokParsingTestCase {

  @Override
  protected boolean shouldCompareCodeBlocks() {
    return false;
  }

  public void testGetter$GetterAccessLevel() {
    doTest(true);
  }

  public void testGetter$GetterAlreadyExists() {
    doTest(true);
  }

  public void testGetter$GetterBoolean() {
    doTest(true);
  }

  public void testGetter$GetterDeprecated() {
    doTest(true);
  }

  public void testGetter$GetterEnum() {
    doTest(true);
  }

  public void ignore_testGetter$GetterLazy() {
    //TODO known problem, try to fix later
    doTest(true);
  }

  public void ignore_testGetter$GetterLazyBoolean() {
    //TODO known problem, try to fix later
    doTest(true);
  }

  public void ignore_testGetter$GetterLazyEahcToString() {
    //TODO known problem, try to fix later
    doTest(true);
  }

  public void testGetter$GetterLazyInvalid() {
    doTest(true);
  }

  public void ignore_testGetter$GetterLazyNative() {
    //TODO known problem, try to fix later
    doTest(true);
  }

  public void testGetter$GetterNone() {
    doTest(true);
  }

  public void testGetter$GetterOnClass() {
    doTest(true);
  }

  public void testGetter$GetterOnMethod() {
    doTest(true);
  }

  public void testGetter$GetterOnMethodErrors() {
    doTest(true);
  }

  public void testGetter$GetterOnMethodErrors2() {
    doTest(true);
  }

  public void testGetter$GetterOnStatic() {
    doTest(true);
  }

  public void testGetter$GetterPlain() {
    doTest(true);
  }

  public void testGetter$GetterWithDollar() {
    doTest(true);
  }

  public void testGetter$MultiFieldGetter() {
    doTest(true);
  }

  public void testGetter$TrickyTypeResolution() {
    doTest(true);
  }

  public void testGetter$ClassNamedAfterGetter() {
    doTest(true);
  }

  public void testGetter$CommentsInterspersed() {
    doTest(true);
  }
}
