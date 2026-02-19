package de.plushnikov.intellij.plugin.problem;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.util.InspectionMessage;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * @author Plushnikov Michail
 */
public class LombokProblemInstance implements LombokProblem {

  private final @NotNull ProblemHighlightType highlightType;
  private final @InspectionMessage @NotNull String message;
  private @NotNull LocalQuickFix @Nullable [] quickFixes;

  public LombokProblemInstance(@InspectionMessage @NotNull String message, @NotNull ProblemHighlightType highlightType) {
    this.message = message;
    this.highlightType = highlightType;
    this.quickFixes = LocalQuickFix.EMPTY_ARRAY;
  }

  @Override
  public void withLocalQuickFixes(Supplier<@Nullable LocalQuickFix>... quickFixSuppliers) {
    this.quickFixes = ContainerUtil.map2Array(quickFixSuppliers, LocalQuickFix.class, Supplier<LocalQuickFix>::get);
  }

  @Override
  public @NotNull ProblemHighlightType getHighlightType() {
    return highlightType;
  }

  @Override
  public @NotNull LocalQuickFix @Nullable [] getQuickFixes() {
    return quickFixes;
  }

  @Override
  public @InspectionMessage @NotNull String getMessage() {
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
