package de.plushnikov.intellij.plugin.problem;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.util.InspectionMessage;

import java.util.function.Supplier;

public interface LombokProblem {
  LombokProblem BLACKHOLE = new LombokProblem() {
    @Override
    public void withLocalQuickFixes(Supplier<LocalQuickFix>... quickFixSuppliers) {
    }

    @Override
    public ProblemHighlightType getHighlightType() {
      return ProblemHighlightType.INFORMATION;
    }

    @Override
    public LocalQuickFix[] getQuickFixes() {
      return LocalQuickFix.EMPTY_ARRAY;
    }

    @Override
    public String getMessage() {
      return null;
    }
  };

  void withLocalQuickFixes(Supplier<LocalQuickFix>... quickFixSuppliers);

  ProblemHighlightType getHighlightType();

  LocalQuickFix[] getQuickFixes();

  @InspectionMessage
  String getMessage();
}
