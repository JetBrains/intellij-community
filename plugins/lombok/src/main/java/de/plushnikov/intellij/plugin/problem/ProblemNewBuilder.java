package de.plushnikov.intellij.plugin.problem;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.util.InspectionMessage;

import java.util.HashSet;
import java.util.Set;

/**
 * @author Plushnikov Michail
 */
public class ProblemNewBuilder implements ProblemBuilder {
  private final Set<LombokProblem> problems;

  public ProblemNewBuilder() {
    this(1);
  }

  public ProblemNewBuilder(int size) {
    this.problems = new HashSet<>(size);
  }

  public Set<LombokProblem> getProblems() {
    return problems;
  }

  @Override
  public void addWarning(@InspectionMessage String message) {
    addProblem(message, ProblemHighlightType.GENERIC_ERROR_OR_WARNING);
  }

  @Override
  public void addWarning(@InspectionMessage String message, Object... params) {
    addProblem(String.format(message, params), ProblemHighlightType.GENERIC_ERROR_OR_WARNING);
  }

  @Override
  public void addError(@InspectionMessage String message) {
    addProblem(message, ProblemHighlightType.GENERIC_ERROR);
  }

  @Override
  public void addError(@InspectionMessage String message, Object... params) {
    addProblem(String.format(message, params), ProblemHighlightType.GENERIC_ERROR);
  }

  @Override
  public void addWarning(@InspectionMessage String message, LocalQuickFix... quickFixes) {
    addProblem(message, ProblemHighlightType.GENERIC_ERROR_OR_WARNING, quickFixes);
  }

  @Override
  public void addError(@InspectionMessage String message, LocalQuickFix... quickFixes) {
    addProblem(message, ProblemHighlightType.GENERIC_ERROR, quickFixes);
  }

  @Override
  public void addProblem(@InspectionMessage String message, ProblemHighlightType highlightType, LocalQuickFix... quickFixes) {
    problems.add(new LombokProblem(message, highlightType, quickFixes));
  }
}
