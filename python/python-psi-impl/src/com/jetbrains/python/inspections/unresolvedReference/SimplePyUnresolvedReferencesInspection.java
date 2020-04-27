package com.jetbrains.python.inspections.unresolvedReference;

import com.intellij.codeInspection.LocalInspectionToolSession;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiReference;
import com.jetbrains.python.codeInsight.dataflow.scope.ScopeUtil;
import com.jetbrains.python.codeInsight.imports.AutoImportQuickFix;
import com.jetbrains.python.codeInsight.imports.PythonImportUtils;
import com.jetbrains.python.inspections.PyInspection;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.psi.PyFunction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class SimplePyUnresolvedReferencesInspection extends PyInspection {
  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder,
                                        boolean isOnTheFly,
                                        @NotNull LocalInspectionToolSession session) {
    return new Visitor(holder, session);
  }

  @Nullable
  @Override
  public String getStaticDescription() {
    return "";
  }

  private static class Visitor extends PyUnresolvedReferencesVisitor {
    Visitor(@Nullable ProblemsHolder holder,
            @NotNull LocalInspectionToolSession session) {
      super(holder, session, Collections.emptyList());
    }

    @Override
    boolean isEnabled(@NotNull PsiElement anchor) {
      return true;
    }

    @Override
    boolean ignoreUnresolved(@NotNull PyElement node, @NotNull PsiReference reference) {
      return false;
    }

    @Override
    Iterable<LocalQuickFix> getAutoImportFixes(PyElement node, PsiReference reference, PsiElement element) {
      List<LocalQuickFix> fixes = new ArrayList<>();

      AutoImportQuickFix fix = PythonImportUtils.proposeImportFix(node, reference);
      if (fix != null) {
        fixes.add(fix);
        if (ScopeUtil.getScopeOwner(node) instanceof PyFunction) {
          fixes.add(fix.forLocalImport());
        }
      }

      return fixes;
    }
  }
}
