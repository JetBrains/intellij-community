package com.intellij.debugger.streams.exec;

import com.intellij.execution.ExecutionException;

import java.lang.reflect.InvocationTargetException;

/**
 * @author Vitaliy.Bibaev
 */
public class AccessibilityFailedTest extends FailEvaluationTest {

  /**
   * Now, evaluation of such test case is not supported. MagicAccessorImpl cannot be parent for a subclass of the class "Super"
   * */
  public void testAccessNotObjectSubclass() throws InterruptedException, ExecutionException, InvocationTargetException {
    doTest(false);
  }
}
