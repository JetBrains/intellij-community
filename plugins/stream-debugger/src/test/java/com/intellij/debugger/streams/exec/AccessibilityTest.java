package com.intellij.debugger.streams.exec;

import com.intellij.execution.ExecutionException;

import java.lang.reflect.InvocationTargetException;

/**
 * @author Vitaliy.Bibaev
 */
public class AccessibilityTest extends TraceExecutionTestCase {
  public void testAccessToPrivateMethodsInStaticContext() throws InterruptedException, ExecutionException, InvocationTargetException {
    doTest(false);
  }

  public void testAccessToPrivateMethods() throws InterruptedException, ExecutionException, InvocationTargetException {
    doTest(false);
  }

  public void testAccessToPrivateClassInStaticContext() throws InterruptedException, ExecutionException, InvocationTargetException {
    doTest(false);
  }

  public void testAccessToPrivateClass() throws InterruptedException, ExecutionException, InvocationTargetException {
    doTest(false);
  }

  public void testAccessToPrivateClassWithMethodReference() throws InterruptedException, ExecutionException, InvocationTargetException {
    doTest(false);
  }
}
