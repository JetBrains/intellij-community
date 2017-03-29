package com.intellij.debugger.streams.exec;

import com.intellij.debugger.streams.trace.TracingResult;
import com.intellij.debugger.streams.wrapper.StreamChain;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.process.ProcessOutputTypes;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.InvocationTargetException;

/**
 * @author Vitaliy.Bibaev
 */
public class FailEvaluationTest extends TraceExecutionTestCase {
  public void testExceptionWhenEvaluating() throws InterruptedException, ExecutionException, InvocationTargetException {
    doTest(false);
  }

  @Override
  protected void handleResults(@Nullable StreamChain chain,
                               @Nullable TracingResult result,
                               @Nullable String evaluationError,
                               boolean resultMustBeNull) {
    assertNotNull(chain);
    assertNull(result);
    assertNotNull(evaluationError);

    println(evaluationError, ProcessOutputTypes.SYSTEM);
  }
}
