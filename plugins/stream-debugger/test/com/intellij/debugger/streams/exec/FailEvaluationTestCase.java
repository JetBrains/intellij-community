// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.streams.exec;

import com.intellij.debugger.streams.test.TraceExecutionTestCase;
import com.intellij.debugger.streams.trace.TracingResult;
import com.intellij.debugger.streams.wrapper.StreamChain;
import com.intellij.execution.process.ProcessOutputTypes;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Vitaliy.Bibaev
 */
public abstract class FailEvaluationTestCase extends TraceExecutionTestCase {

  @Override
  protected void handleError(@NotNull StreamChain chain, @NotNull String error, @NotNull TraceExecutionTestCase.FailureReason reason) {
    println(StringUtil.capitalize(reason.toString().toLowerCase()) + " failed", ProcessOutputTypes.SYSTEM);
    println(error, ProcessOutputTypes.SYSTEM);
  }

  @Override
  protected void handleSuccess(@Nullable StreamChain chain, @Nullable TracingResult result, boolean resultMustBeNull) {
    fail();
  }
}
