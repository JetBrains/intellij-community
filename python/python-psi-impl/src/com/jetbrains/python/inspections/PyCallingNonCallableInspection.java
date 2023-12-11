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
import com.intellij.codeInspection.util.InspectionMessage;
import com.intellij.psi.PsiElementVisitor;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.PyPsiBundle;
import com.jetbrains.python.inspections.quickfix.PyRemoveCallQuickFix;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.types.PyClassType;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.PyTypeChecker;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


public final class PyCallingNonCallableInspection extends PyInspection {

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder,
                                        boolean isOnTheFly,
                                        @NotNull LocalInspectionToolSession session) {
    return new Visitor(holder, PyInspectionVisitor.getContext(session));
  }

  public static class Visitor extends PyInspectionVisitor {
    public Visitor(@Nullable ProblemsHolder holder,
                   @NotNull TypeEvalContext context) {
      super(holder, context);
    }

    @Override
    public void visitPyCallExpression(@NotNull PyCallExpression node) {
      super.visitPyCallExpression(node);
      checkCallable(node, node.getCallee());
    }

    @Override
    public void visitPyDecorator(@NotNull PyDecorator decorator) {
      super.visitPyDecorator(decorator);
      final PyExpression callee = decorator.getCallee();
      checkCallable(decorator, callee);
      if (decorator.hasArgumentList()) {
        checkCallable(decorator, decorator);
      }
    }

    private void checkCallable(@NotNull PyElement node, @Nullable PyExpression callee) {
      if (node.getParent() instanceof PyDecorator) return; //we've already been here

      if (callee != null && isCallable(callee, myTypeEvalContext) == Boolean.FALSE) {
        final PyType calleeType = myTypeEvalContext.getType(callee);
        @InspectionMessage String message = PyPsiBundle.message("INSP.expression.is.not.callable");
        if (calleeType instanceof PyClassType) {
          message = PyPsiBundle.message("INSP.class.object.is.not.callable", calleeType.getName());
        }
        else {
          final String name = callee.getName();
          if (name != null) {
            message = PyPsiBundle.message("INSP.symbol.is.not.callable", name);
          }
        }
        registerProblem(node, message, new PyRemoveCallQuickFix());
      }
    }
  }

  @Nullable
  private static Boolean isCallable(@NotNull PyExpression element, @NotNull TypeEvalContext context) {
    if (element instanceof PyQualifiedExpression && PyNames.__CLASS__.equals(element.getName())) {
      return true;
    }
    return PyTypeChecker.isCallable(context.getType(element));
  }
}
