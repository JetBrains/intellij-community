package de.plushnikov.intellij.plugin.problem;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemHighlightType;

/**
 * @author Plushnikov Michail
 */
public interface ProblemBuilder {
  void addWarning(String message);

  void addWarning(String message, Object... params);

  void addError(String message);

  void addError(String message, Object... params);

  void addWarning(String message, LocalQuickFix... quickFixes);

  void addError(String message, LocalQuickFix... quickFixes);

  void addProblem(String message, ProblemHighlightType highlightType, LocalQuickFix... quickFixes);
}
