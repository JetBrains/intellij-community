package de.plushnikov.intellij.plugin.problem;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.util.InspectionMessage;
import com.intellij.util.containers.ContainerUtil;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * @author Plushnikov Michail
 */
public class LombokProblemInstance implements LombokProblem {

  private final ProblemHighlightType highlightType;
  @InspectionMessage
  private final String message;
  private LocalQuickFix[] quickFixes;

  public LombokProblemInstance(@InspectionMessage String message, ProblemHighlightType highlightType) {
    this.message = message;
    this.highlightType = highlightType;
    this.quickFixes = LocalQuickFix.EMPTY_ARRAY;
  }

  @Override
  public void withLocalQuickFixes(Supplier<LocalQuickFix>... quickFixSuppliers) {
    this.quickFixes = ContainerUtil.map2Array(quickFixSuppliers, LocalQuickFix.class, Supplier<LocalQuickFix>::get);
  }

  @Override
  public ProblemHighlightType getHighlightType() {
    return highlightType;
  }

  @Override
  public LocalQuickFix[] getQuickFixes() {
    return quickFixes;
  }

  @Override
  @InspectionMessage
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

    LombokProblemInstance that = (LombokProblemInstance)o;

    return Objects.equals(message, that.message);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(message);
  }
}
