// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.streams.trace.impl.interpret.ex;

import org.jetbrains.annotations.NotNull;

/**
 * @author Vitaliy.Bibaev
 */
class ResolveException extends IllegalStateException {
  ResolveException(@NotNull String s) {
    super(s);
  }

  ResolveException(@NotNull String message, @NotNull Throwable cause) {
    super(message, cause);
  }
}
