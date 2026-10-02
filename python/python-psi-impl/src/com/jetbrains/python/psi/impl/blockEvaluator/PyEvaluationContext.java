// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi.impl.blockEvaluator;

import com.jetbrains.python.psi.PyFile;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * Cache for {@link PyBlockEvaluator}.
 * You may obtain one via {@link PyBlockEvaluator#getContext()} and pass to ctor:
 * {@link PyBlockEvaluator#PyBlockEvaluator(PyEvaluationContext)} to enable cache
 *
 * @author Ilya.Kazakevich
 */
@ApiStatus.Internal
public class PyEvaluationContext {
  private final @NotNull Map<PyFile, PyEvaluationResult> myResultMap = new HashMap<>();

  PyEvaluationContext() {
  }

  /**
   * Get evaluation result by file
   * @param file file
   * @return eval result
   */
  @Nullable
  PyEvaluationResult getCachedResult(final @NotNull PyFile file) {
    return myResultMap.get(file);
  }

  /**
   * Store evaluation result by file
   * @param file file
   * @param result evaluation result
   */
  void cache(final @NotNull PyFile file, final @NotNull PyEvaluationResult result) {
    myResultMap.put(file, result);
  }
}
