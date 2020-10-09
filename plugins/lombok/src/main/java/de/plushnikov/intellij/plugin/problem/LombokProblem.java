package de.plushnikov.intellij.plugin.problem;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemHighlightType;

/**
 * @author Plushnikov Michail
 */
public class LombokProblem {
  private static final LocalQuickFix[] EMPTY_QUICK_FIX = new LocalQuickFix[0];

  private final ProblemHighlightType highlightType;
  private final LocalQuickFix[] quickFixes;
  private final String message;

  public LombokProblem(String message) {
    this(message, ProblemHighlightType.GENERIC_ERROR_OR_WARNING, EMPTY_QUICK_FIX);
  }

  public LombokProblem(String message, ProblemHighlightType highlightType) {
    this(message, highlightType, EMPTY_QUICK_FIX);
  }

  public LombokProblem(String message, LocalQuickFix... quickFixes) {
    this(message, ProblemHighlightType.GENERIC_ERROR_OR_WARNING, quickFixes);
  }

  public LombokProblem(String message, ProblemHighlightType highlightType, LocalQuickFix... quickFixes) {
    this.message = message;
    this.highlightType = highlightType;
    this.quickFixes = quickFixes;
  }

  public ProblemHighlightType getHighlightType() {
    return highlightType;
  }

  public LocalQuickFix[] getQuickFixes() {
    return quickFixes;
  }

  public String getMessage() {
    return message;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    LombokProblem that = (LombokProblem) o;

    return !(message != null ? !message.equals(that.message) : that.message != null);
  }

  @Override
  public int hashCode() {
    return message != null ? message.hashCode() : 0;
  }
}
