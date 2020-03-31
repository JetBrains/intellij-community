/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.jetbrains.python.psi.impl.blockEvaluator;

import com.jetbrains.python.psi.PyFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * Cache for {@link com.jetbrains.python.psi.impl.blockEvaluator.PyBlockEvaluator}.
 * You may obtain one via {@link PyBlockEvaluator#getContext()} and pass to ctor:
 * {@link com.jetbrains.python.psi.impl.blockEvaluator.PyBlockEvaluator#PyBlockEvaluator(PyEvaluationContext)} to enable cache
 *
 * @author Ilya.Kazakevich
 */
public class PyEvaluationContext {
  @NotNull
  private final Map<PyFile, PyEvaluationResult> myResultMap = new HashMap<>();

  PyEvaluationContext() {
  }

  /**
   * Get evaluation result by file
   * @param file file
   * @return eval result
   */
  @Nullable
  PyEvaluationResult getCachedResult(@NotNull final PyFile file) {
    return myResultMap.get(file);
  }

  /**
   * Store evaluation result by file
   * @param file file
   * @param result evaluation result
   */
  void cache(@NotNull final PyFile file, @NotNull final PyEvaluationResult result) {
    myResultMap.put(file, result);
  }
}
