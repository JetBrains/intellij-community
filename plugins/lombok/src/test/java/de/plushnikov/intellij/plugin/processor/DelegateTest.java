package de.plushnikov.intellij.plugin.processor;

import de.plushnikov.intellij.plugin.AbstractLombokParsingTestCase;

/**
 * Unit tests for IntelliJPlugin for Lombok, based on lombok test classes
 */
public class DelegateTest extends AbstractLombokParsingTestCase {

  public void ignore_testDelegate$DelegateOnGetter() throws Exception {
    //TODO known problem, try to fix later
    doTest();
  }

  public void testDelegate$DelegateOnGetterNone() throws Exception {
    doTest();
  }

  public void testDelegate$DelegateOnMethods() throws Exception {
    doTest();
  }

  public void testDelegate$DelegateRecursion() throws Exception {
    doTest();
  }

  public void testDelegate$DelegateTypesAndExcludes() throws Exception {
    doTest();
  }

  public void testDelegate$DelegateWithDeprecated() throws Exception {
    doTest();
  }

  public void testDelegate$DelegateWithException() throws Exception {
    doTest();
  }

  public void testDelegate$DelegateGenericInterfaceIssue88() throws Exception {
    doTest();
  }
}