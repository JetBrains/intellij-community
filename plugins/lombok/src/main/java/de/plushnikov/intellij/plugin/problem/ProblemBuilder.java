package de.plushnikov.intellij.plugin.problem;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.util.InspectionMessage;

/**
 * @author Plushnikov Michail
 */
public interface ProblemBuilder {
  void addWarning(@InspectionMessage  String message);

  void addWarning(@InspectionMessage String message, Object... params);

  void addError(@InspectionMessage String message);

  void addError(@InspectionMessage String message, Object... params);

  void addWarning(@InspectionMessage String message, LocalQuickFix... quickFixes);

  void addError(@InspectionMessage String message, LocalQuickFix... quickFixes);

  void addProblem(@InspectionMessage String message, ProblemHighlightType highlightType, LocalQuickFix... quickFixes);
}
