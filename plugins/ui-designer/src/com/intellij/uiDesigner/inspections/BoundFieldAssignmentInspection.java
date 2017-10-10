// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

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

/**
 * @author yole
 */
public class BoundFieldAssignmentInspection extends AbstractBaseJavaLocalInspectionTool {
  @Override
  @NotNull
  public String getGroupDisplayName() {
    return UIDesignerBundle.message("form.inspections.group");
  }

  @Override
  @NotNull
  public String getDisplayName() {
    return UIDesignerBundle.message("inspection.bound.field.title");
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
      public void visitAssignmentExpression(PsiAssignmentExpression expression) {
        PsiExpression lExpression = expression.getLExpression();
        if (lExpression instanceof PsiReferenceExpression) {
          PsiReferenceExpression lExpr = (PsiReferenceExpression)lExpression;
          PsiElement lElement = lExpr.resolve();
          if (!(lElement instanceof PsiField)) {
            return;
          }
          PsiField field = (PsiField) lElement;
          PsiReference formReference = FormReferenceProvider.getFormReference(field);
          if (!(formReference instanceof FieldFormReference)) {
            return;
          }
          FieldFormReference ref = (FieldFormReference) formReference;
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
