// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.streams.core.diagnostic.ex;

import com.intellij.openapi.diagnostic.Attachment;
import com.intellij.openapi.diagnostic.RuntimeExceptionWithAttachments;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * @author Vitaliy.Bibaev
 */
@ApiStatus.Internal
public abstract class TraceException extends RuntimeExceptionWithAttachments {
  TraceException(@NotNull String message, @NotNull String traceExpression) {
    super(message, new Attachment("trace.txt", traceExpression));
  }
}
