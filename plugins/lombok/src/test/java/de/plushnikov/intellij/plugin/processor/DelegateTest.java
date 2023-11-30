package de.plushnikov.intellij.plugin.processor;

import de.plushnikov.intellij.plugin.AbstractLombokParsingTestCase;

/**
 * Unit tests for IntelliJPlugin for Lombok, based on lombok test classes
 */
public class DelegateTest extends AbstractLombokParsingTestCase {

  public void testDelegate$DelegateAlreadyImplemented() {
    doTest(true);
  }

  public void testDelegate$DelegateGenerics() {
    doTest(true);
  }

  public void ignore_testDelegate$DelegateOnGetter() {
    //TODO known problem, try to fix later
    doTest(true);
  }

  public void testDelegate$DelegateOnGetterNone() {
    doTest(true);
  }

  public void testDelegate$DelegateOnMethods() {
    doTest(true);
  }

  public void testDelegate$DelegateRecursion() {
    doTest(true);
  }

  public void testDelegate$DelegateTypesAndExcludes() {
    doTest(true);
  }

  public void testDelegate$DelegateWithDeprecated() {
    doTest(true);
  }

  public void testDelegate$DelegateWithException() {
    doTest(true);
  }

  public void testDelegate$DelegateWithVarargs() {
    doTest(true);
  }

  public void testDelegate$DelegateWithVarargs2() {
    doTest(true);
  }


  public void testDelegate$DelegateGenericInterfaceIssue88() {
    doTest(true);
  }
}
