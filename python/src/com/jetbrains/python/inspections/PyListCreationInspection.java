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
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.inspections.quickfix.ListCreationQuickFix;
import com.jetbrains.python.psi.*;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * User :catherine
 */
public class PyListCreationInspection extends PyInspection {
  @Nls
  @NotNull
  @Override
  public String getDisplayName() {
    return PyBundle.message("INSP.NAME.list.creation");
  }

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder,
                                        boolean isOnTheFly,
                                        @NotNull LocalInspectionToolSession session) {
    return new Visitor(holder, session);
  }

  private static class Visitor extends PyInspectionVisitor {
    public Visitor(@Nullable ProblemsHolder holder, @NotNull LocalInspectionToolSession session) {
      super(holder, session);
    }

    @Override
    public void visitPyAssignmentStatement(PyAssignmentStatement node) {
      if (!(node.getAssignedValue() instanceof PyListLiteralExpression))return;
      final PyExpression[] targets = node.getTargets();
      if (targets.length != 1) return;
      final PyExpression target = targets[0];
      final String name = target.getName();
      if (name == null) return;

      PyStatement expressionStatement = PsiTreeUtil.getNextSiblingOfType(node, PyStatement.class);
      if (!(expressionStatement instanceof PyExpressionStatement))
        return;

      ListCreationQuickFix quickFix = null;

      final String message = "This list creation could be rewritten as a list literal";
      while (expressionStatement instanceof PyExpressionStatement) {
        final PyExpression statement = ((PyExpressionStatement)expressionStatement).getExpression();
        if (!(statement instanceof PyCallExpression)) break;

        final PyCallExpression callExpression = (PyCallExpression)statement;
        final PyExpression callee = callExpression.getCallee();
        if (callee instanceof PyQualifiedExpression) {
          final PyExpression qualifier = ((PyQualifiedExpression)callee).getQualifier();
          final String funcName = ((PyQualifiedExpression)callee).getReferencedName();
          if (qualifier != null && name.equals(qualifier.getText()) && "append".equals(funcName)) {
            final PyArgumentList argList = callExpression.getArgumentList();
            if (argList != null) {
              for (PyExpression argument : argList.getArguments()) {
                if (argument.getText().equals(name)) {
                  if (quickFix != null)
                    registerProblem(node, message, quickFix);
                  return;
                }
              }
              if (quickFix == null) {
                quickFix = new ListCreationQuickFix(node);
              }
              quickFix.addStatement((PyExpressionStatement)expressionStatement);
            }
          }
        }
        if (quickFix == null) {
          return;
        }
        expressionStatement = PsiTreeUtil.getNextSiblingOfType(expressionStatement, PyStatement.class);
      }

      if (quickFix != null) {
        registerProblem(node, message, quickFix);
      }
    }
  }
}
