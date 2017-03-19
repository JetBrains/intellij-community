package com.intellij.debugger.streams.exec;

import com.intellij.execution.ExecutionException;

import java.lang.reflect.InvocationTargetException;

/**
 * @author Vitaliy.Bibaev
 */
public class TerminalOperationTest extends TraceExecutionTestCase {
  public void testForEach() throws InterruptedException, ExecutionException, InvocationTargetException {
    doTest(true);
  }

  public void testForEachOrdered() throws InterruptedException, ExecutionException, InvocationTargetException {
    doTest(true);
  }
}
