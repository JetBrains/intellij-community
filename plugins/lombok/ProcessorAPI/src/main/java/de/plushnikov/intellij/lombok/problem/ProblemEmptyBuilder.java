package de.plushnikov.intellij.lombok.problem;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemHighlightType;

/**
 * @author Plushnikov Michail
 */
public class ProblemEmptyBuilder implements ProblemBuilder {

  public void addProblem(String message) {
  }

  public void addError(String message) {

  }

  public void addProblem(String message, ProblemHighlightType highlightType) {
  }

  public void addProblem(String message, LocalQuickFix... quickFixes) {
  }

  public void addProblem(String message, ProblemHighlightType highlightType, LocalQuickFix... quickFixes) {
  }
}
