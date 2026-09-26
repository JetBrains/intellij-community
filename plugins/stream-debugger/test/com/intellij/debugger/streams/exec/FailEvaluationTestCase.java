// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.streams.exec;

import com.intellij.debugger.streams.core.testFramework.TraceExecutionTestHelper;
import com.intellij.debugger.streams.core.trace.TracingResult;
import com.intellij.debugger.streams.core.wrapper.StreamChain;
import com.intellij.debugger.streams.test.ExecutionTestCaseHelper;
import com.intellij.debugger.streams.test.TraceExecutionTestCase;
import com.intellij.execution.process.ProcessOutputTypes;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.xdebugger.XDebugSession;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Vitaliy.Bibaev
 */
public abstract class FailEvaluationTestCase extends TraceExecutionTestCase {
  @Override
  protected @NotNull TraceExecutionTestHelper getHelper(XDebugSession session) {
    return new ExecutionTestCaseHelper(this, session, getLibrarySupportProvider(), myPositionResolver, LOG) {
      @Override
      protected void handleSuccess(@NotNull StreamChain chain, @NotNull TracingResult result, @Nullable Boolean resultMustBeNull) {
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
}
