package de.plushnikov.intellij.lombok.problem;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemHighlightType;

/**
 * @author Plushnikov Michail
 */
public class LombokProblem {
  public static final LocalQuickFix[] EMPTY_QUICK_FIXS = new LocalQuickFix[0];

  private final ProblemHighlightType highlightType;
  private final LocalQuickFix[] quickFixes;
  private final String message;

  public LombokProblem(String message) {
    this(message, ProblemHighlightType.GENERIC_ERROR_OR_WARNING, EMPTY_QUICK_FIXS);
  }

  public LombokProblem(String message, ProblemHighlightType highlightType) {
    this(message, highlightType, EMPTY_QUICK_FIXS);
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

}
