package de.plushnikov.intellij.lombok.problem;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemHighlightType;

import java.util.Collection;

/**
 * @author Plushnikov Michail
 */
public class ProblemNewBuilder implements ProblemBuilder {
  private Collection<LombokProblem> target;

  public ProblemNewBuilder(Collection<LombokProblem> target) {
    this.target = target;
  }

  public void addWarning(String message) {
    addProblem(message, ProblemHighlightType.GENERIC_ERROR_OR_WARNING);
  }

  public void addError(String message) {
    addProblem(message, ProblemHighlightType.GENERIC_ERROR);
  }

  public void addWarning(String message, LocalQuickFix... quickFixes) {
    addProblem(message, ProblemHighlightType.GENERIC_ERROR_OR_WARNING, quickFixes);
  }

  public void addError(String message, LocalQuickFix... quickFixes) {
    addProblem(message, ProblemHighlightType.GENERIC_ERROR, quickFixes);
  }

  public void addProblem(String message, ProblemHighlightType highlightType, LocalQuickFix... quickFixes) {
    target.add(new LombokProblem(message, highlightType, quickFixes));
  }
}
