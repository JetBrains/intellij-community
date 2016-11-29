/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.theoryinpractice.testng.inspection;

import com.intellij.codeInspection.BaseJavaBatchLocalInspectionTool;
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
public class AssertEqualsBetweenInconvertibleTypesTestNGInspection extends BaseJavaBatchLocalInspectionTool {
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