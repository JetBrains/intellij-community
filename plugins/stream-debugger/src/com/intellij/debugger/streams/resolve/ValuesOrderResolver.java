// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.streams.resolve;

import com.intellij.debugger.streams.trace.TraceElement;
import com.intellij.debugger.streams.trace.TraceInfo;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;

/**
 * @author Vitaliy.Bibaev
 */
public interface ValuesOrderResolver {
  @NotNull
  Result resolve(@NotNull TraceInfo info);

  interface Result {
    @NotNull
    Map<TraceElement, List<TraceElement>> getDirectOrder();

    @NotNull
    Map<TraceElement, List<TraceElement>> getReverseOrder();

    static Result of(@NotNull Map<TraceElement, List<TraceElement>> direct, @NotNull Map<TraceElement, List<TraceElement>> reverse) {
      return new Result() {
        @NotNull
        @Override
        public Map<TraceElement, List<TraceElement>> getDirectOrder() {
          return direct;
        }

        @NotNull
        @Override
        public Map<TraceElement, List<TraceElement>> getReverseOrder() {
          return reverse;
        }
      };
    }
  }
}
