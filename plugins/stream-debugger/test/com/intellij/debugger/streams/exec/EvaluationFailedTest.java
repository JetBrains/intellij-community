// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.streams.exec;

import com.intellij.debugger.streams.core.testFramework.TraceExecutionTestHelper;
import com.intellij.debugger.streams.core.trace.TraceExpressionBuilder;
import com.intellij.debugger.streams.core.trace.TracingResult;
import com.intellij.debugger.streams.core.wrapper.StreamChain;
import com.intellij.debugger.streams.test.ExecutionTestCaseHelper;
import com.intellij.debugger.ui.impl.watch.CompilingEvaluator;
import com.intellij.execution.process.ProcessOutputTypes;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.xdebugger.XDebugSession;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;

/**
 * @author Vitaliy.Bibaev
 */
@RunWith(JUnit4.class)
public class EvaluationFailedTest extends FailEvaluationTestCase {
  @Test
  public void testEvaluationExceptionDetected() {
    assumeFalse(Registry.is("debugger.evaluate.method.helper"));
    doTest(true, "EvaluationExceptionDetected");
  }

  @Test
  public void testEvaluationExceptionDetectedHelper() {
    assumeTrue(Registry.is("debugger.evaluate.method.helper"));
    doTest(true, "EvaluationExceptionDetected");
  }

  @Override
  protected @NotNull TraceExecutionTestHelper getHelper(XDebugSession session) {
    return new ExecutionTestCaseHelper(this, session, getLibrarySupportProvider(), myPositionResolver, LOG) {
      @Override
      protected @NotNull TraceExpressionBuilder createExpressionBuilder() {
        final TraceExpressionBuilder builder = super.createExpressionBuilder();
        return chain -> "if(true) throw new RuntimeException(\"My exception message\");\n" + builder.createTraceExpression(chain);
      }

      @Override
      protected void handleSuccess(@NotNull StreamChain chain,
                                   @NotNull TracingResult result,
                                   @Nullable Boolean resultMustBeNull) {
        fail();
      }

      @Override
      protected void handleError(@NotNull StreamChain chain,
                                 @NotNull String error,
                                 @NotNull TraceExecutionTestHelper.FailureReason reason) {
        println(StringUtil.capitalize(reason.toString().toLowerCase()) + " failed", ProcessOutputTypes.SYSTEM);
        println(error, ProcessOutputTypes.SYSTEM);
      }
    };
  }

  @NotNull
  @Override
  protected String replaceAdditionalInOutput(@NotNull String str) {
    String output = super.replaceAdditionalInOutput(str);
    String generatedClassName = CompilingEvaluator.getGeneratedClassName();
    StringBuilder builder = new StringBuilder();
    for (String line : StringUtil.splitByLinesKeepSeparators(output)) {
      if (line.trim().startsWith("at ") && line.contains(generatedClassName)) {
        line = StringUtil.substringBefore(line, "at") + "at !GENERATED_EVALUATION_CLASS!" + StringUtil.substringAfterLast(line, ")");
      }
      builder.append(line);
    }
    return builder.toString();
  }
}
