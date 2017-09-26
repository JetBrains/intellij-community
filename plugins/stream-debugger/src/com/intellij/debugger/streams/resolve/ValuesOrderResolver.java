/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
