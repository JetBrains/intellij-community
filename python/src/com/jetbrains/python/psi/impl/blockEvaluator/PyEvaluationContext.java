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
  private final Map<PyFile, PyEvaluationResult> myResultMap = new HashMap<PyFile, PyEvaluationResult>();

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
