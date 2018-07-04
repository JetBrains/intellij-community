// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.theoryinpractice.testng.inspection;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInsight.ExceptionUtil;
import com.intellij.codeInspection.AbstractBaseJavaLocalInspectionTool;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.*;
import com.intellij.psi.util.InheritanceUtil;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author Bas Leijdekkers
 */
public class ExpectedExceptionNeverThrownTestNGInspection extends AbstractBaseJavaLocalInspectionTool {

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    return new ExpectedExceptionNeverThrownVisitor(holder);
  }

  private static class ExpectedExceptionNeverThrownVisitor extends JavaElementVisitor {

    private final ProblemsHolder myProblemsHolder;

    public ExpectedExceptionNeverThrownVisitor(ProblemsHolder problemsHolder) {
      myProblemsHolder = problemsHolder;
    }

    @Override
    public void visitMethod(PsiMethod method) {
      super.visitMethod(method);
      final PsiAnnotation annotation = AnnotationUtil.findAnnotation(method, "org.testng.annotations.Test");
      if (annotation == null) {
        return;
      }
      final PsiAnnotationMemberValue value = annotation.findDeclaredAttributeValue("expectedExceptions");
      if (!(value instanceof PsiClassObjectAccessExpression)) {
        return;
      }
      final PsiCodeBlock body = method.getBody();
      if (body == null) {
        return;
      }
      final PsiClassObjectAccessExpression classObjectAccessExpression = (PsiClassObjectAccessExpression)value;
      final PsiTypeElement operand = classObjectAccessExpression.getOperand();
      final PsiType type = operand.getType();
      if (!(type instanceof PsiClassType)) {
        return;
      }
      final PsiClassType classType = (PsiClassType)type;
      final PsiClass aClass = classType.resolve();
      if (InheritanceUtil.isInheritor(aClass, CommonClassNames.JAVA_LANG_RUNTIME_EXCEPTION)) {
        return;
      }
      final List<PsiClassType> exceptionsThrown = ExceptionUtil.getThrownExceptions(body);
      for (PsiClassType psiClassType : exceptionsThrown) {
        if (psiClassType.isAssignableFrom(classType)) {
          return;
        }
      }
      myProblemsHolder.registerProblem(operand, "Expected <code>#ref</code> never thrown in body of '" + method.getName() + "()' #loc");
    }
  }
}
