package de.plushnikov.intellij.plugin.processor;

import de.plushnikov.intellij.plugin.LombokParsingTestCase;

/**
 * Unit tests for IntelliJPlugin for Lombok, based on lombok test classes
 */
public class DelegateTest extends LombokParsingTestCase {

  public void ignore_testDelegateOnGetter() throws Exception {
    //TODO known problem, try to fix later
    doTest();
  }

  public void testDelegateOnGetterNone() throws Exception {
    doTest();
  }

  public void testDelegateOnMethods() throws Exception {
    doTest();
  }

  public void testDelegateRecursion() throws Exception {
    doTest();
  }

  public void testDelegateTypesAndExcludes() throws Exception {
    doTest();
  }

  public void testDelegateWithDeprecated() throws Exception {
    doTest();
  }

  public void testDelegateWithException() throws Exception {
    doTest();
  }

  public void testDelegateGenericInterfaceIssue88() throws Exception {
    doTest();
  }
}