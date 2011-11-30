package de.plushnikov.intellij.lombok.problem;

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

  public void addWarning(String message) {
  }

  public void addError(String message) {
  }

  public void addWarning(String message, LocalQuickFix... quickFixes) {
  }

  public void addError(String message, LocalQuickFix... quickFixes) {
  }

  public void addProblem(String message, ProblemHighlightType highlightType, LocalQuickFix... quickFixes) {
  }
}
