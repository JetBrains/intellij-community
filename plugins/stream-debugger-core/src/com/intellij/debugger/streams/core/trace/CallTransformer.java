// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.streams.core.trace;

import com.intellij.debugger.streams.core.wrapper.StreamCall;
import org.jetbrains.annotations.NotNull;

public interface CallTransformer<T extends StreamCall> {
  default @NotNull T transformCall(@NotNull T call) {
    return call;
  }
}