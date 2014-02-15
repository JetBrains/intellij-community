package de.plushnikov.intellij.plugin.problem;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemHighlightType;

/**
 * @author Plushnikov Michail
 */
public class ProblemEmptyBuilder implements ProblemBuilder {

  private static ProblemEmptyBuilder instance = new ProblemEmptyBuilder();

  public static ProblemEmptyBuilder getInstance() {
    return instance;
  }

  private ProblemEmptyBuilder() {
  }

  @Override
  public void addWarning(String message) {
  }

  @Override
  public void addError(String message) {
  }

  @Override
  public void addWarning(String message, Object... params) {
  }

  @Override
  public void addError(String message, Object... params) {
  }

  @Override
  public void addWarning(String message, LocalQuickFix... quickFixes) {
  }

  @Override
  public void addError(String message, LocalQuickFix... quickFixes) {
  }

  @Override
  public void addProblem(String message, ProblemHighlightType highlightType, LocalQuickFix... quickFixes) {
  }
}
