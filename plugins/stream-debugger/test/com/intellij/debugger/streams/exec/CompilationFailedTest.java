// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.streams.exec;

import com.intellij.debugger.streams.trace.TraceExpressionBuilder;
import com.intellij.debugger.streams.wrapper.StreamChain;
import org.jetbrains.annotations.NotNull;

/**
 * @author Vitaliy.Bibaev
 */
public class CompilationFailedTest extends FailEvaluationTestCase {
  public void testCompilationErrorDetected()  {
    doTest(true);
  }

  @Override
  protected TraceExpressionBuilder getExpressionBuilder() {
    final TraceExpressionBuilder builder = super.getExpressionBuilder();
    return chain -> "float a = 0.;\n" + builder.createTraceExpression(chain);
  }

  @Override
  protected void handleError(@NotNull StreamChain chain, @NotNull String error, @NotNull FailureReason reason) {
    assertEquals(FailureReason.COMPILATION, reason);
    super.handleError(chain, error, reason);
  }
}
