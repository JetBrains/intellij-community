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
package com.jetbrains.python.inspections;

import com.intellij.codeInspection.LocalInspectionToolSession;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiReference;
import com.intellij.util.containers.Stack;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.types.PyClassType;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * User: catherine
 *
 * Inspection to detect too broad except clause
 * such as no exception class specified, or specified as 'Exception'
 */
public class PyBroadExceptionInspection extends PyInspection {
  @Nls
  @NotNull
  @Override
  public String getDisplayName() {
    return PyBundle.message("INSP.NAME.too.broad.exception.clauses");
  }

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder,
                                        boolean isOnTheFly,
                                        @NotNull LocalInspectionToolSession session) {
    return new Visitor(holder, session);
  }

  public static boolean equalsException(@NotNull PyClass cls, @NotNull TypeEvalContext context) {
    final PyType type = context.getType(cls);
    return ("Exception".equals(cls.getName()) || "BaseException".equals(cls.getName())) && type != null && type.isBuiltin();
  }

  private static class Visitor extends PyInspectionVisitor {
    public Visitor(@Nullable ProblemsHolder holder, @NotNull LocalInspectionToolSession session) {
      super(holder, session);
    }

    @Override
    public void visitPyExceptBlock(final PyExceptPart node){
      PyExpression exceptClass = node.getExceptClass();
      if (reRaised(node))
        return;
      if (exceptClass == null) {
        registerProblem(node.getFirstChild(), "Too broad exception clause");
      }
      if (exceptClass != null) {
        final PyType type = myTypeEvalContext.getType(exceptClass);
        if (type instanceof PyClassType) {
          final PyClass cls = ((PyClassType)type).getPyClass();
          final PyExpression target = node.getTarget();
          if (equalsException(cls, myTypeEvalContext) && (target == null || !isExceptionUsed(node, target.getText()))) {
            registerProblem(exceptClass, "Too broad exception clause");
          }
        }
      }
    }

    private static boolean reRaised(PyExceptPart node) {
      final PyStatementList statementList = node.getStatementList();
      if (statementList != null) {
        for (PyStatement st : statementList.getStatements()) {
          if (st instanceof PyRaiseStatement)
            return true;
        }
      }
      return false;
    }

    private static boolean isExceptionUsed(PyExceptPart node, String text) {
      Stack<PsiElement> stack = new Stack<>();
      PyStatementList statementList = node.getStatementList();
      if (statementList != null) {
        for (PyStatement st : statementList.getStatements()) {
          stack.push(st);
          while (!stack.isEmpty()) {
            PsiElement e = stack.pop();
            if (e instanceof PyReferenceExpression) {
              PsiReference reference = e.getReference();
              if (reference != null) {
                PsiElement resolved = reference.resolve();
                if (resolved != null) {
                  if (resolved.getText().equals(text))
                    return true;
                }
              }
            }
            for (PsiElement psiElement : e.getChildren()) {
              stack.push(psiElement);
            }
          }
        }
      }
      return false;
    }
  }
}
