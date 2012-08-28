/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

import com.intellij.codeInspection.BaseJavaLocalInspectionTool;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.InheritanceUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * @author Bas Leijdekkers
 */
public class MisorderedAssertEqualsArgumentsTestNGInspection extends BaseJavaLocalInspectionTool {

  private static class FlipParametersFix implements LocalQuickFix {

    @Override
    @NotNull
    public String getName() {
      return "Flip compared arguments";
    }

    @NotNull
    @Override
    public String getFamilyName() {
      return getName();
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      final PsiElement methodNameIdentifier = descriptor.getPsiElement();
      final PsiElement parent = methodNameIdentifier.getParent();
      if (parent == null) {
        return;
      }
      final PsiMethodCallExpression callExpression = (PsiMethodCallExpression)parent.getParent();
      if (callExpression == null) {
        return;
      }
      final PsiReferenceExpression methodExpression = callExpression.getMethodExpression();
      final PsiMethod method = (PsiMethod)methodExpression.resolve();
      if (method == null) {
        return;
      }
      final PsiClass containingClass = method.getContainingClass();
      final boolean junit;
      if (InheritanceUtil.isInheritor(containingClass, "org.testng.Assert")) {
        junit = false;
      }
      else if (InheritanceUtil.isInheritor(containingClass, "org.testng.AssertJUnit")) {
        junit = true;
      }
      else {
        return;
      }
      final PsiParameterList parameterList = method.getParameterList();
      final PsiParameter[] parameters = parameterList.getParameters();
      final PsiManager psiManager = callExpression.getManager();
      final GlobalSearchScope scope = GlobalSearchScope.allScope(project);
      final PsiType stringType = PsiType.getJavaLangString(psiManager, scope);
      final PsiType parameterType1 = parameters[0].getType();
      final PsiExpressionList argumentList = callExpression.getArgumentList();
      final PsiExpression[] arguments = argumentList.getExpressions();
      final PsiExpression expectedArgument;
      final PsiExpression actualArgument;
      if (junit) {
        if (parameterType1.equals(stringType) && parameters.length > 2) {
          expectedArgument = arguments[1];
          actualArgument = arguments[2];
        }
        else {
          expectedArgument = arguments[0];
          actualArgument = arguments[1];
        }
      }
      else {
        actualArgument = arguments[0];
        expectedArgument = arguments[1];
      }
      final PsiElement copy = expectedArgument.copy();
      expectedArgument.replace(actualArgument);
      actualArgument.replace(copy);
    }
  }

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    return new MisorderedAssertEqualsParametersVisitor(holder);
  }

  private static class MisorderedAssertEqualsParametersVisitor extends JavaElementVisitor {

    private final ProblemsHolder myProblemsHolder;

    public MisorderedAssertEqualsParametersVisitor(ProblemsHolder problemsHolder) {
      myProblemsHolder = problemsHolder;
    }

    @Override
    public void visitMethodCallExpression(@NotNull PsiMethodCallExpression expression) {
      super.visitMethodCallExpression(expression);
      final PsiReferenceExpression methodExpression = expression.getMethodExpression();
      @NonNls final String methodName = methodExpression.getReferenceName();
      if (!"assertEquals".equals(methodName) && !"assertEqualsNoOrder".equals(methodName) &&
          !"assertNotEquals".equals(methodName) && !"assertArrayEquals".equals(methodName)) {
        return;
      }
      final PsiMethod method = expression.resolveMethod();
      if (method == null) {
        return;
      }
      final PsiManager manager = expression.getManager();
      final Project project = manager.getProject();
      final GlobalSearchScope scope = GlobalSearchScope.allScope(project);
      final PsiType stringType = PsiType.getJavaLangString(manager, scope);
      final PsiExpressionList argumentList = expression.getArgumentList();
      final PsiExpression[] arguments = argumentList.getExpressions();
      if (arguments.length < 2) {
        return;
      }
      final PsiClass containingClass = method.getContainingClass();
      final PsiExpression expectedArgument;
      final PsiExpression actualArgument;
      if (InheritanceUtil.isInheritor(containingClass, "org.testng.AssertJUnit")) {
        final PsiType firstArgumentType = arguments[0].getType();
        if (stringType.equals(firstArgumentType) && arguments.length > 2) {
          expectedArgument = arguments[1];
          actualArgument = arguments[2];
        }
        else {
          expectedArgument = arguments[0];
          actualArgument = arguments[1];
        }
      } else if (InheritanceUtil.isInheritor(containingClass, "org.testng.Assert")){
        actualArgument = arguments[0];
        expectedArgument = arguments[1];
      } else {
        return;
      }
      if (expectedArgument == null || actualArgument == null) {
        return;
      }
      if (isLiteralOrConstant(expectedArgument)) {
        return;
      }
      if (!isLiteralOrConstant(actualArgument)) {
        return;
      }
      final PsiElement referenceNameElement = methodExpression.getReferenceNameElement();
      if (referenceNameElement != null) {
        myProblemsHolder.registerProblem(referenceNameElement, "Arguments to <code>#ref()</code> in wrong order #loc",
                                         new FlipParametersFix());
      }
      else {
        myProblemsHolder.registerProblem(methodExpression, "Arguments to <code>#ref()</code> in wrong order #loc",
                                         new FlipParametersFix());
      }
    }

    private static boolean isLiteralOrConstant(PsiExpression expression) {
      if (expression instanceof PsiLiteralExpression) {
        return true;
      }
      else if (expression instanceof PsiNewExpression) {
        final PsiNewExpression newExpression = (PsiNewExpression)expression;
        final PsiExpressionList argumentList = newExpression.getArgumentList();
        if (argumentList == null) {
          return true;
        }
        for (PsiExpression argument : argumentList.getExpressions()) {
          if (!isLiteralOrConstant(argument)) {
            return false;
          }
        }
        return true;
      }
      if (!(expression instanceof PsiReferenceExpression)) {
        return false;
      }
      final PsiReferenceExpression referenceExpression = (PsiReferenceExpression)expression;
      final PsiElement target = referenceExpression.resolve();
      if (!(target instanceof PsiField)) {
        return false;
      }
      final PsiField field = (PsiField)target;
      return field.hasModifierProperty(PsiModifier.STATIC) && field.hasModifierProperty(PsiModifier.FINAL);
    }
  }
}