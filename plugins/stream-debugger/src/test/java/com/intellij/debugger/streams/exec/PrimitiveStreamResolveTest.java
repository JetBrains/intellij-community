package com.intellij.debugger.streams.exec;

import com.intellij.execution.ExecutionException;

import java.lang.reflect.InvocationTargetException;

/**
 * @author Vitaliy.Bibaev
 */
public class PrimitiveStreamResolveTest extends TraceExecutionTestCase {
  public void testDistinctPrimitive() throws InterruptedException, ExecutionException, InvocationTargetException {
    doTest(false);
  }

  public void testDistinctHardPrimitive() throws InterruptedException, ExecutionException, InvocationTargetException {
    doTest(false);
  }

  public void testFilterPrimitive() throws InterruptedException, ExecutionException, InvocationTargetException {
    doTest(false);
  }

  public void testMapPrimitive() throws InterruptedException, ExecutionException, InvocationTargetException {
    doTest(false);
  }

  public void testFlatMapPrimitive() throws InterruptedException, ExecutionException, InvocationTargetException {
    doTest(false);
  }

  public void testSortedPrimitive() throws InterruptedException, ExecutionException, InvocationTargetException {
    doTest(false);
  }
}
