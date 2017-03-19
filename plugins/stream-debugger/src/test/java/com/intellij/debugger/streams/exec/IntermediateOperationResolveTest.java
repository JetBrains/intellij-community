package com.intellij.debugger.streams.exec;

import com.intellij.execution.ExecutionException;

import java.lang.reflect.InvocationTargetException;

/**
 * @author Vitaliy.Bibaev
 */
public class IntermediateOperationResolveTest extends TraceExecutionTestCase {
  public void testFilter() throws InterruptedException, ExecutionException, InvocationTargetException {
    doTest(false);
  }

  public void testMap() throws InterruptedException, ExecutionException, InvocationTargetException {
    doTest(false);
  }

  public void testFlatMap() throws InterruptedException, ExecutionException, InvocationTargetException {
    doTest(false);
  }

  public void testDistinctSame() throws InterruptedException, ExecutionException, InvocationTargetException {
    doTest(false);
  }

  public void testDistinctEquals() throws InterruptedException, ExecutionException, InvocationTargetException {
    doTest(false);
  }

  public void testDistinctHardCase() throws InterruptedException, ExecutionException, InvocationTargetException {
    doTest(false);
  }

  public void testSorted() throws InterruptedException, ExecutionException, InvocationTargetException {
    doTest(false);
  }

  public void testPeek() throws InterruptedException, ExecutionException, InvocationTargetException {
    doTest(false);
  }

  public void testSkip() throws InterruptedException, ExecutionException, InvocationTargetException {
    doTest(false);
  }

  public void testLimit() throws InterruptedException, ExecutionException, InvocationTargetException {
    doTest(false);
  }
}
