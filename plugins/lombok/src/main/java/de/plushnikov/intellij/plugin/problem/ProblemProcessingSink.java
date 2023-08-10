package de.plushnikov.intellij.plugin.problem;

import org.jetbrains.annotations.NotNull;

/**
 * @author Plushnikov Michail
 */
public class ProblemProcessingSink implements ProblemSink {

  private boolean validationFailed = false;

  @Override
  public boolean deepValidation() {
    return false;
  }

  @Override
  public boolean success() {
    return !validationFailed;
  }

  @Override
  public void markFailed() {
    validationFailed = true;
  }

  @Override
  public LombokProblem addWarningMessage(@NotNull String key, Object @NotNull ... params) {
    return LombokProblem.BLACKHOLE;
  }

  @Override
  public LombokProblem addErrorMessage(@NotNull String key, Object @NotNull ... params) {
    return LombokProblem.BLACKHOLE;
  }
}
