// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.uiDesigner.inspections;

import com.intellij.codeInspection.AbstractBaseJavaLocalInspectionTool;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.uiDesigner.UIDesignerBundle;
import com.intellij.uiDesigner.binding.FieldFormReference;
import com.intellij.uiDesigner.binding.FormReferenceProvider;
import com.intellij.uiDesigner.compiler.AsmCodeGenerator;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;


public class BoundFieldAssignmentInspection extends AbstractBaseJavaLocalInspectionTool {
  @Override
  @NotNull
  public String getGroupDisplayName() {
    return UIDesignerBundle.message("form.inspections.group");
  }

  @Override
  @NotNull
  @NonNls
  public String getShortName() {
    return "BoundFieldAssignment";
  }

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  @Override
  @NotNull
  public PsiElementVisitor buildVisitor(@NotNull final ProblemsHolder holder, boolean isOnTheFly) {
    return new JavaElementVisitor() {
      @Override
      public void visitAssignmentExpression(@NotNull PsiAssignmentExpression expression) {
        PsiExpression lExpression = expression.getLExpression();
        if (lExpression instanceof PsiReferenceExpression lExpr) {
          PsiElement lElement = lExpr.resolve();
          if (!(lElement instanceof PsiField field)) {
            return;
          }
          PsiReference formReference = FormReferenceProvider.getFormReference(field);
          if (!(formReference instanceof FieldFormReference ref)) {
            return;
          }
          if (ref.isCustomCreate()) {
            return;
          }

          PsiMethod method = PsiTreeUtil.getParentOfType(expression, PsiMethod.class);
          if (method != null && AsmCodeGenerator.SETUP_METHOD_NAME.equals(method.getName())) {
            return;
          }
          holder.registerProblem(expression, UIDesignerBundle.message("inspection.bound.field.message"));
        }
      }
    };
  }
}
