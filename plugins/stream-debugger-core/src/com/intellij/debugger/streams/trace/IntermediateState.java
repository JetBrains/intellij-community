// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.streams.trace;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.util.List;

/**
 * @author Vitaliy.Bibaev
 */
public interface IntermediateState {

  @NotNull
  List<TraceElement> getTrace();

  @Nullable
  Value getStreamResult();
}
