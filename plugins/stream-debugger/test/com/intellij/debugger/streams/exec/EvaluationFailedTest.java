// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.streams.exec;

import com.intellij.debugger.streams.trace.TraceExpressionBuilder;

/**
 * @author Vitaliy.Bibaev
 */
public class EvaluationFailedTest extends FailEvaluationTestCase {
  public void testEvaluationExceptionDetected() {
    doTest(true);
  }

  @Override
  protected TraceExpressionBuilder getExpressionBuilder() {
    final TraceExpressionBuilder builder = super.getExpressionBuilder();
    return chain -> "if(true) throw new RuntimeException(\"My exception message\");\n" + builder.createTraceExpression(chain);
  }
}
