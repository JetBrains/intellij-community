// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.streams.trace;

import com.intellij.util.containers.ContainerUtil;
import com.sun.jdi.Value;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author Vitaliy.Bibaev
 */
public interface IntermediateState {

  @NotNull
  List<TraceElement> getTrace();

  @NotNull
  default List<Value> getRawValues() {
    return ContainerUtil.map(getTrace(), TraceElement::getValue);
  }
}
