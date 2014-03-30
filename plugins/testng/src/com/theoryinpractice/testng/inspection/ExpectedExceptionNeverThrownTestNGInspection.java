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

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInsight.ExceptionUtil;
import com.intellij.codeInspection.BaseJavaLocalInspectionTool;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.*;
import com.intellij.psi.util.InheritanceUtil;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author Bas Leijdekkers
 */
public class ExpectedExceptionNeverThrownTestNGInspection extends BaseJavaLocalInspectionTool {

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
