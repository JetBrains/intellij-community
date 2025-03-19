// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.theoryinpractice.testng.inspection;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInsight.ExceptionUtil;
import com.intellij.codeInspection.AbstractBaseJavaLocalInspectionTool;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.*;
import com.intellij.psi.util.InheritanceUtil;
import com.theoryinpractice.testng.TestngBundle;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author Bas Leijdekkers
 */
public class ExpectedExceptionNeverThrownTestNGInspection extends AbstractBaseJavaLocalInspectionTool {

  @Override
  public @NotNull PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    return new ExpectedExceptionNeverThrownVisitor(holder);
  }

  private static class ExpectedExceptionNeverThrownVisitor extends JavaElementVisitor {

    private final ProblemsHolder myProblemsHolder;

    ExpectedExceptionNeverThrownVisitor(ProblemsHolder problemsHolder) {
      myProblemsHolder = problemsHolder;
    }

    @Override
    public void visitMethod(@NotNull PsiMethod method) {
      super.visitMethod(method);
      final PsiAnnotation annotation = AnnotationUtil.findAnnotation(method, "org.testng.annotations.Test");
      if (annotation == null) {
        return;
      }
      final PsiAnnotationMemberValue value = annotation.findDeclaredAttributeValue("expectedExceptions");
      if (value == null) {
        return;
      }
      final PsiCodeBlock body = method.getBody();
      if (body == null) {
        return;
      }
      final List<PsiClassType> exceptionsThrown = ExceptionUtil.getThrownExceptions(body);
      if (value instanceof PsiClassObjectAccessExpression) {
        checkAnnotationMemberValue(value, method, exceptionsThrown);
      }
      else if (value instanceof PsiArrayInitializerMemberValue arrayInitializerMemberValue) {
        for (PsiAnnotationMemberValue memberValue : arrayInitializerMemberValue.getInitializers()) {
          checkAnnotationMemberValue(memberValue, method, exceptionsThrown);
        }
      }
    }

    private void checkAnnotationMemberValue(PsiAnnotationMemberValue annotationMemberValue, PsiMethod method,
                                            List<PsiClassType> exceptionsThrown) {
      if (!(annotationMemberValue instanceof PsiClassObjectAccessExpression classObjectAccessExpression))  {
        return;
      }
      final PsiTypeElement operand = classObjectAccessExpression.getOperand();
      final PsiType type = operand.getType();
      if (!(type instanceof PsiClassType expectedType)) {
        return;
      }
      if (InheritanceUtil.isInheritor(expectedType, CommonClassNames.JAVA_LANG_RUNTIME_EXCEPTION)) {
        return;
      }
      for (PsiClassType exceptionType : exceptionsThrown) {
        if (exceptionType.isAssignableFrom(expectedType)) {
          return;
        }
      }
      myProblemsHolder.registerProblem(operand, TestngBundle.message("inspection.testng.expected.exception.never.thrown.problem",
                                                                     method.getName()));
    }
  }
}
