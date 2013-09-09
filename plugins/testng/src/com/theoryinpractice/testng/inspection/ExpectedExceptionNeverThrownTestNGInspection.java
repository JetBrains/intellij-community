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
import com.intellij.codeInspection.BaseJavaLocalInspectionTool;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.search.ProjectScope;
import com.intellij.psi.util.InheritanceUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
      final PsiAnnotationMemberValue value = annotation.findAttributeValue("expectedExceptions");
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
      final Set<PsiClassType> exceptionsThrown = calculateExceptionsThrown(body);
      if (exceptionsThrown.contains(classType)) {
        return;
      }
      myProblemsHolder.registerProblem(operand, "Expected <code>#ref</code> never thrown in body of '" + method.getName() + "()' #loc");
    }
  }

  @NotNull
  public static Set<PsiClassType> calculateExceptionsThrown(@NotNull PsiElement element) {
    final ExceptionsThrownVisitor visitor = new ExceptionsThrownVisitor();
    element.accept(visitor);
    return visitor.getExceptionsThrown();
  }

  private static class ExceptionsThrownVisitor extends JavaRecursiveElementVisitor {
    private final Set<PsiClassType> m_exceptionsThrown = new HashSet<PsiClassType>(4);

    @Override
    public void visitMethodCallExpression(@NotNull PsiMethodCallExpression expression) {
      super.visitMethodCallExpression(expression);
      final PsiMethod method = expression.resolveMethod();
      collectExceptionsThrown(method, m_exceptionsThrown);
    }

    @Override
    public void visitNewExpression(@NotNull PsiNewExpression expression) {
      super.visitNewExpression(expression);
      final PsiMethod method = expression.resolveMethod();
      collectExceptionsThrown(method, m_exceptionsThrown);
    }

    @Override
    public void visitThrowStatement(PsiThrowStatement statement) {
      super.visitThrowStatement(statement);
      final PsiExpression exception = statement.getException();
      if (exception == null) {
        return;
      }
      final PsiType type = exception.getType();
      if (!(type instanceof PsiClassType)) {
        return;
      }
      m_exceptionsThrown.add((PsiClassType)type);
    }

    @Override
    public void visitTryStatement(@NotNull PsiTryStatement statement) {
      final Set<PsiType> exceptionsHandled = getExceptionTypesHandled(statement);
      final PsiResourceList resourceList = statement.getResourceList();
      if (resourceList != null) {
        final List<PsiResourceVariable> resourceVariables = resourceList.getResourceVariables();
        for (PsiResourceVariable resourceVariable : resourceVariables) {
          final Set<PsiClassType> resourceExceptions = calculateExceptionsThrown(resourceVariable);
          final PsiType type = resourceVariable.getType();
          if (type instanceof PsiClassType) {
            final PsiClassType classType = (PsiClassType)type;
            collectExceptionsThrown(findAutoCloseableCloseMethod(classType.resolve()), resourceExceptions);
          }
          for (PsiClassType resourceException : resourceExceptions) {
            if (!isExceptionHandled(exceptionsHandled, resourceException)) {
              m_exceptionsThrown.add(resourceException);
            }
          }
        }
      }
      final PsiCodeBlock tryBlock = statement.getTryBlock();
      if (tryBlock != null) {
        final Set<PsiClassType> tryExceptions = calculateExceptionsThrown(tryBlock);
        for (PsiClassType tryException : tryExceptions) {
          if (!isExceptionHandled(exceptionsHandled, tryException)) {
            m_exceptionsThrown.add(tryException);
          }
        }
      }
      final PsiCodeBlock finallyBlock = statement.getFinallyBlock();
      if (finallyBlock != null) {
        final Set<PsiClassType> finallyExceptions = calculateExceptionsThrown(finallyBlock);
        m_exceptionsThrown.addAll(finallyExceptions);
      }

      final PsiCodeBlock[] catchBlocks = statement.getCatchBlocks();
      for (PsiCodeBlock catchBlock : catchBlocks) {
        final Set<PsiClassType> catchExceptions = calculateExceptionsThrown(catchBlock);
        m_exceptionsThrown.addAll(catchExceptions);
      }
    }

    private static void collectExceptionsThrown(@Nullable PsiMethod method, @NotNull Set<PsiClassType> out) {
      if (method == null) {
        return;
      }
      final PsiElementFactory factory = JavaPsiFacade.getElementFactory(method.getProject());
      final PsiJavaCodeReferenceElement[] referenceElements = method.getThrowsList().getReferenceElements();
      for (PsiJavaCodeReferenceElement referenceElement : referenceElements) {
        final PsiClass exceptionClass = (PsiClass)referenceElement.resolve();
        if (exceptionClass != null) {
          out.add(factory.createType(exceptionClass));
        }
      }
    }

    @Nullable
    private static PsiMethod findAutoCloseableCloseMethod(@Nullable PsiClass aClass) {
      if (aClass == null || !InheritanceUtil.isInheritor(aClass, CommonClassNames.JAVA_LANG_AUTO_CLOSEABLE)) {
        return null;
      }
      final Project project = aClass.getProject();
      final JavaPsiFacade facade = JavaPsiFacade.getInstance(project);
      final PsiClass autoCloseable = facade.findClass(CommonClassNames.JAVA_LANG_AUTO_CLOSEABLE, ProjectScope.getLibrariesScope(project));
      if (autoCloseable == null) {
        return null;
      }
      final PsiMethod closeMethod = autoCloseable.findMethodsByName("close", false)[0];
      return aClass.findMethodBySignature(closeMethod, true);
    }

    private static boolean isExceptionHandled(Iterable<PsiType> exceptionsHandled, PsiType thrownType) {
      for (PsiType exceptionHandled : exceptionsHandled) {
        if (exceptionHandled.isAssignableFrom(thrownType)) {
          return true;
        }
      }
      return false;
    }

    private static Set<PsiType> getExceptionTypesHandled(@NotNull PsiTryStatement statement) {
      final Set<PsiType> out = new HashSet<PsiType>(5);
      final PsiParameter[] parameters = statement.getCatchBlockParameters();
      for (PsiParameter parameter : parameters) {
        final PsiType type = parameter.getType();
        out.add(type);
      }
      return out;
    }

    @NotNull
    public Set<PsiClassType> getExceptionsThrown() {
      return m_exceptionsThrown;
    }
  }
}
