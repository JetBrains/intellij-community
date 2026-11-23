// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.streams.exec;

import com.intellij.debugger.streams.core.testFramework.TraceExecutionTestHelper;
import com.intellij.debugger.streams.core.trace.TraceExpressionBuilder;
import com.intellij.debugger.streams.core.trace.TracingResult;
import com.intellij.debugger.streams.core.wrapper.StreamChain;
import com.intellij.debugger.streams.test.ExecutionTestCaseHelper;
import com.intellij.debugger.streams.test.TraceExecutionFixture;
import com.intellij.execution.process.ProcessOutputTypes;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.xdebugger.XDebugSession;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

import static com.intellij.debugger.streams.test.TraceExecutionFixtureKt.traceExecutionFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * @author Vitaliy.Bibaev
 */
@TestApplication
public class CompilationFailedTest {
  private final TestFixture<TraceExecutionFixture> traceFixture = traceExecutionFixture(this::getHelper);

  @Test
  public void testCompilationErrorDetected() {
    traceFixture.get().doTest(true);
  }

  private @NotNull TraceExecutionTestHelper getHelper(@NotNull TraceExecutionFixture trace, @NotNull XDebugSession session) {
    return new ExecutionTestCaseHelper(trace, session) {
      @Override
      protected @NotNull TraceExpressionBuilder createExpressionBuilder() {
        final TraceExpressionBuilder builder = super.createExpressionBuilder();
        return chain -> "float a = 0.;\n" + builder.createTraceExpression(chain);
      }

      @Override
      protected void handleSuccess(@NotNull StreamChain chain,
                                   @NotNull TracingResult result,
                                   @Nullable Boolean resultMustBeNull) {
        fail();
      }

      @Override
      protected void handleError(@NotNull StreamChain chain, @NotNull String error, @NotNull FailureReason reason) {
        assertEquals(FailureReason.COMPILATION, reason);
        println(StringUtil.capitalize(reason.toString().toLowerCase()) + " failed", ProcessOutputTypes.SYSTEM);
        println(error, ProcessOutputTypes.SYSTEM);
      }
    };
  }
}
