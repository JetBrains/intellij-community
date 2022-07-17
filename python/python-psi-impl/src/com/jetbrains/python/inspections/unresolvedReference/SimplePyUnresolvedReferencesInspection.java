package com.jetbrains.python.inspections.unresolvedReference;

import com.intellij.codeInspection.LocalInspectionToolSession;
import com.intellij.codeInspection.ProblemsHolder;
import com.jetbrains.python.inspections.PyInspection;
import com.jetbrains.python.inspections.PyInspectionVisitor;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;

public class SimplePyUnresolvedReferencesInspection extends PyUnresolvedReferencesInspectionBase {
  @Override
  protected PyUnresolvedReferencesVisitor createVisitor(@NotNull ProblemsHolder holder,
                                                        @NotNull LocalInspectionToolSession session) {
    return new Visitor(holder, this, PyInspectionVisitor.getContext(session));
  }

  @Nullable
  @Override
  public String getStaticDescription() {
    return null;
  }

  public static class Visitor extends PyUnresolvedReferencesVisitor {
    public Visitor(@Nullable ProblemsHolder holder,
                   @NotNull PyInspection inspection,
                   @NotNull TypeEvalContext context) {
      super(holder, Collections.emptyList(), inspection, context);
    }
  }
}
