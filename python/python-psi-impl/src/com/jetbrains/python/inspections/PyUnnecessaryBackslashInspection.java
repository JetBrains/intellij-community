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
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.containers.Stack;
import com.jetbrains.python.PyPsiBundle;
import com.jetbrains.python.inspections.quickfix.RemoveUnnecessaryBackslashQuickFix;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * User: catherine
 * <p/>
 * Inspection to highlight backslashes in places where line continuation is implicit (inside (), [], {}).
 */
public class PyUnnecessaryBackslashInspection extends PyInspection {

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder,
                                        boolean isOnTheFly,
                                        @NotNull LocalInspectionToolSession session) {
    return new Visitor(holder, PyInspectionVisitor.getContext(session));
  }

  public static class Visitor extends PyInspectionVisitor {
    public Visitor(@Nullable ProblemsHolder holder, @NotNull TypeEvalContext context) {
      super(holder, context);
    }

    @Override
    public void visitPyParameterList(final @NotNull PyParameterList list) {
      findProblem(list);
    }

    @Override
    public void visitPyArgumentList(final @NotNull PyArgumentList list) {
      findProblem(list);
    }

    @Override
    public void visitPyTupleExpression(@NotNull PyTupleExpression node) {
      if (node.getParent() instanceof PyParenthesizedExpression)
        findProblem(node);
    }

    @Override
    public void visitPyParenthesizedExpression(final @NotNull PyParenthesizedExpression expression) {
      final Stack<PsiElement> stack = new Stack<>();
      stack.push(expression);
      while (!stack.isEmpty()) {
        PsiElement element = stack.pop();
        if (!(element instanceof PyTupleExpression)) {
          findProblem(element);
          if (element != null) {
            for (PsiElement psiElement : element.getChildren()) {
              stack.push(psiElement);
            }
          }
        }
      }
    }

    @Override
    public void visitPyDictLiteralExpression(final @NotNull PyDictLiteralExpression expression) {
      findProblem(expression);
    }

    @Override
    public void visitPyListLiteralExpression(final @NotNull PyListLiteralExpression expression) {
      findProblem(expression);
    }

    @Override
    public void visitPySetLiteralExpression(final @NotNull PySetLiteralExpression expression) {
      findProblem(expression);
    }

    @Override
    public void visitPyStringLiteralExpression(final @NotNull PyStringLiteralExpression stringLiteralExpression) {
      PsiElement parent = stringLiteralExpression.getParent();
      if (parent instanceof PyListLiteralExpression || parent instanceof PyParenthesizedExpression ||
          parent instanceof PySetLiteralExpression || parent instanceof PyKeyValueExpression ||
          parent instanceof PyNamedParameter || parent instanceof PyArgumentList) {
        findProblem(stringLiteralExpression);
      }
    }

    private void findProblem(@Nullable final PsiElement expression) {
      final PsiWhiteSpace[] children = PsiTreeUtil.getChildrenOfType(expression, PsiWhiteSpace.class);
      if (children != null) {
        for (PsiWhiteSpace ws : children) {
          if (ws.getText().contains("\\")) {
            registerProblem(ws, PyPsiBundle.message("INSP.unnecessary.backslash.unnecessary.backslash.in.expression"),
                            new RemoveUnnecessaryBackslashQuickFix());
          }
        }
      }
    }

  }
}
