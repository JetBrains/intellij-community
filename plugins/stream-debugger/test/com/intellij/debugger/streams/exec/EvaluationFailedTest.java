// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.streams.exec;

import com.intellij.debugger.streams.core.testFramework.TraceExecutionTestHelper;
import com.intellij.debugger.streams.core.trace.TraceExpressionBuilder;
import com.intellij.debugger.streams.core.trace.TracingResult;
import com.intellij.debugger.streams.core.wrapper.StreamChain;
import com.intellij.debugger.streams.test.ExecutionTestCaseHelper;
import com.intellij.debugger.streams.test.TraceExecutionFixture;
import com.intellij.debugger.ui.impl.watch.CompilingEvaluator;
import com.intellij.execution.process.ProcessOutputTypes;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.xdebugger.XDebugSession;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

import static com.intellij.debugger.streams.test.TraceExecutionFixtureKt.traceExecutionFixture;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * @author Vitaliy.Bibaev
 */
@TestApplication
public class EvaluationFailedTest {
  private final TestFixture<TraceExecutionFixture> traceFixture = traceExecutionFixture(this::getHelper, EvaluationFailedTest::replaceAdditionalInOutput);

  @Test
  public void testEvaluationExceptionDetected() {
    assumeTrue("off".equals(Registry.get("debugger.evaluate.method.helper").getSelectedOption()));
    traceFixture.get().doTest(true, "EvaluationExceptionDetected");
  }

  @Test
  public void testEvaluationExceptionDetectedHelper() {
    assumeTrue("always".equals(Registry.get("debugger.evaluate.method.helper").getSelectedOption()));
    traceFixture.get().doTest(true, "EvaluationExceptionDetected");
  }

  private @NotNull TraceExecutionTestHelper getHelper(@NotNull TraceExecutionFixture trace, @NotNull XDebugSession session) {
    return new ExecutionTestCaseHelper(trace, session) {
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

  private static @NotNull String replaceAdditionalInOutput(@NotNull String output) {
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
