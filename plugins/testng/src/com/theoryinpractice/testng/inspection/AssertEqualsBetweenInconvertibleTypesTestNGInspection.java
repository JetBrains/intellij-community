/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.InheritanceUtil;
import com.siyeh.ig.psiutils.TypeUtils;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * @author Bas Leijdekkers
 */
public class AssertEqualsBetweenInconvertibleTypesTestNGInspection extends BaseJavaBatchLocalInspectionTool {
  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    return new AssertEqualsBetweenInconvertibleTypesVisitor(holder);
  }

  private static class AssertEqualsBetweenInconvertibleTypesVisitor extends JavaElementVisitor {

    private final ProblemsHolder myProblemsHolder;

    public AssertEqualsBetweenInconvertibleTypesVisitor(ProblemsHolder problemsHolder) {
      myProblemsHolder = problemsHolder;
    }

    @Override
    public void visitMethodCallExpression(@NotNull PsiMethodCallExpression expression) {
      super.visitMethodCallExpression(expression);
      final PsiReferenceExpression methodExpression = expression.getMethodExpression();
      @NonNls final String methodName = methodExpression.getReferenceName();
      if (!"assertEquals".equals(methodName)) {
        return;
      }
      final PsiMethod method = expression.resolveMethod();
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
      if (parameters.length < 2) {
        return;
      }
      final PsiType firstParameterType = parameters[0].getType();
      final PsiExpressionList argumentList = expression.getArgumentList();
      final PsiExpression[] arguments = argumentList.getExpressions();
      final PsiExpression expression1;
      final PsiExpression expression2;
      final PsiType parameterType1;
      final PsiType parameterType2;
      if (junit && firstParameterType.equalsToText(CommonClassNames.JAVA_LANG_STRING)) {
        if (arguments.length < 3) {
          return;
        }
        expression1 = arguments[1];
        expression2 = arguments[2];
        parameterType1 = parameters[1].getType();
        parameterType2 = parameters[2].getType();
      }
      else {
        if (arguments.length < 2) {
          return;
        }
        expression1 = arguments[0];
        expression2 = arguments[1];
        parameterType1 = parameters[0].getType();
        parameterType2 = parameters[1].getType();
      }
      final PsiType type1 = expression1.getType();
      if (type1 == null) {
        return;
      }
      final PsiType type2 = expression2.getType();
      if (type2 == null) {
        return;
      }
      final PsiManager manager = expression.getManager();
      final GlobalSearchScope scope = expression.getResolveScope();
      final PsiClassType objectType = PsiType.getJavaLangObject(manager, scope);
      if (!objectType.equals(parameterType1) || !objectType.equals(parameterType2)) {
        return;
      }
      if (TypeUtils.areConvertible(type1, type2)) {
        return;
      }
      final PsiElement referenceNameElement = methodExpression.getReferenceNameElement();
      if (referenceNameElement == null) {
        return;
      }
      myProblemsHolder.registerProblem(referenceNameElement,
                                       "<code>#ref()</code> between objects of inconvertible types '" +
                                       StringUtil.escapeXml(type1.getPresentableText()) + "' and '" +
                                       StringUtil.escapeXml(type2.getPresentableText()) + "' #loc");
    }
  }
}