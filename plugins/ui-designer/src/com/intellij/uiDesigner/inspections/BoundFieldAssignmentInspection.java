/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.intellij.uiDesigner.inspections;

import com.intellij.codeInspection.BaseJavaLocalInspectionTool;
import com.intellij.codeInspection.LocalQuickFix;
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
public class BoundFieldAssignmentInspection extends BaseJavaLocalInspectionTool {
  @NotNull
  public String getGroupDisplayName() {
    return UIDesignerBundle.message("form.inspections.group");
  }

  @NotNull
  public String getDisplayName() {
    return UIDesignerBundle.message("inspection.bound.field.title");
  }

  @NotNull
  @NonNls
  public String getShortName() {
    return "BoundFieldAssignment";
  }

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  @NotNull
  public PsiElementVisitor buildVisitor(@NotNull final ProblemsHolder holder, boolean isOnTheFly) {
    return new JavaElementVisitor() {
      @Override
      public void visitAssignmentExpression(PsiAssignmentExpression expression) {
        if (expression.getLExpression() instanceof PsiReferenceExpression) {
          PsiMethod method = PsiTreeUtil.getParentOfType(expression, PsiMethod.class);
          if (method != null && AsmCodeGenerator.SETUP_METHOD_NAME.equals(method.getName())) {
            return;
          }
          PsiReferenceExpression lExpr = (PsiReferenceExpression) expression.getLExpression();
          PsiElement lElement = lExpr.resolve();
          if (lElement instanceof PsiField) {
            PsiField field = (PsiField) lElement;
            PsiReference formReference = FormReferenceProvider.getFormReference(field);
            if (formReference instanceof FieldFormReference) {
              FieldFormReference ref = (FieldFormReference) formReference;
              if (!ref.isCustomCreate()) {
                holder.registerProblem(expression, UIDesignerBundle.message("inspection.bound.field.message"),
                                       new LocalQuickFix[0]);
              }
            }
          }
        }
      }
    };
  }
}
