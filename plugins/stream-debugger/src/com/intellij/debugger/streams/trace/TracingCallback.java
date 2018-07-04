// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.streams.trace;

import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import org.jetbrains.annotations.NotNull;

/**
 * @author Vitaliy.Bibaev
 */
public interface TracingCallback {
  void evaluated(@NotNull TracingResult result, @NotNull EvaluationContextImpl context);

  void evaluationFailed(@NotNull String traceExpression, @NotNull String message);

  void compilationFailed(@NotNull String traceExpression, @NotNull String message);
}
