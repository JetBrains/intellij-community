// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.theoryinpractice.testng.inspection;

import com.intellij.codeInspection.AbstractBaseJavaLocalInspectionTool;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.JavaElementVisitor;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiMethodCallExpression;
import com.siyeh.ig.testFrameworks.AssertHint;
import org.jetbrains.annotations.NotNull;

/**
 * @author Bas Leijdekkers
 */
public class AssertEqualsBetweenInconvertibleTypesTestNGInspection extends
                                                                   AbstractBaseJavaLocalInspectionTool {
  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    return new JavaElementVisitor() {
      @Override
      public void visitMethodCallExpression(@NotNull PsiMethodCallExpression expression) {
        super.visitMethodCallExpression(expression);
        final String errorMessage = AssertHint.areExpectedActualTypesCompatible(expression, true);
        if (errorMessage != null) {
          final PsiElement referenceNameElement = expression.getMethodExpression().getReferenceNameElement();
          if (referenceNameElement == null) {
            return;
          }

          holder.registerProblem(referenceNameElement, errorMessage);
        }
      }
    };
  }
}