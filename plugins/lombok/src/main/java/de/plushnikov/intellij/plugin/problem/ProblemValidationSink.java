package de.plushnikov.intellij.plugin.problem;

import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.util.InspectionMessage;
import de.plushnikov.intellij.plugin.LombokBundle;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.Set;

/**
 * @author Plushnikov Michail
 */
public class ProblemValidationSink implements ProblemSink {
  private final Set<LombokProblem> problems = new HashSet<>();
  private boolean validationFailed = false;

  @Override
  public boolean deepValidation() {
    return true;
  }

  @Override
  public boolean success() {
    return !validationFailed;
  }

  @Override
  public void markFailed() {
    validationFailed = true;
  }

  public Set<LombokProblem> getProblems() {
    return problems;
  }

  @Override
  public LombokProblemInstance addWarningMessage(@NotNull String key, Object @NotNull ... params) {
    return addProblem(LombokBundle.message(key, params), ProblemHighlightType.GENERIC_ERROR_OR_WARNING);
  }

  @Override
  public LombokProblemInstance addErrorMessage(@NotNull String key, Object @NotNull ... params) {
    return addProblem(LombokBundle.message(key, params), ProblemHighlightType.GENERIC_ERROR);
  }

  private LombokProblemInstance addProblem(@InspectionMessage String message,
                                           ProblemHighlightType highlightType) {
    final LombokProblemInstance lombokProblem = new LombokProblemInstance(message, highlightType);
    problems.add(lombokProblem);
    return lombokProblem;
  }
}
