package de.plushnikov.intellij.plugin.problem;

import de.plushnikov.intellij.plugin.LombokBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.PropertyKey;

/**
 * @author Plushnikov Michail
 */
public interface ProblemSink {
  boolean deepValidation();

  boolean success();

  void markFailed();

  LombokProblem addWarningMessage(@NotNull @PropertyKey(resourceBundle = LombokBundle.PATH_TO_BUNDLE) String key, Object @NotNull ... params);

  LombokProblem addErrorMessage(@NotNull @PropertyKey(resourceBundle = LombokBundle.PATH_TO_BUNDLE) String key, Object @NotNull ... params);

}
