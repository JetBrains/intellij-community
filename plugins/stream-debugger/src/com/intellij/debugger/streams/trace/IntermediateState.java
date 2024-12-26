// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.streams.trace;

import com.intellij.util.containers.ContainerUtil;
import com.sun.jdi.Value;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.util.List;

/**
 * @author Vitaliy.Bibaev
 */
public interface IntermediateState {

  @NotNull
  List<TraceElement> getTrace();

  default @NotNull @Unmodifiable List<Value> getRawValues() {
    return ContainerUtil.map(getTrace(), TraceElement::getValue);
  }
}
