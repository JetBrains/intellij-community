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
import com.jetbrains.python.PyNames;
import com.jetbrains.python.inspections.quickfix.PyRemoveCallQuickFix;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.types.PyClassType;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.PyTypeChecker;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author yole
 */
public class PyCallingNonCallableInspection extends PyInspection {
  @Nls
  @NotNull
  @Override
  public String getDisplayName() {
    return "Trying to call a non-callable object";
  }

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder,
                                        boolean isOnTheFly,
                                        @NotNull LocalInspectionToolSession session) {
    return new Visitor(holder, session);
  }

  public static class Visitor extends PyInspectionVisitor {
    public Visitor(@Nullable ProblemsHolder holder, @NotNull LocalInspectionToolSession session) {
      super(holder, session);
    }

    @Override
    public void visitPyCallExpression(PyCallExpression node) {
      super.visitPyCallExpression(node);
      checkCallable(node, node.getCallee());
    }

    @Override
    public void visitPyDecoratorList(PyDecoratorList node) {
      super.visitPyDecoratorList(node);
      for (PyDecorator decorator : node.getDecorators()) {
        final PyExpression callee = decorator.getCallee();
        checkCallable(decorator, callee);
        if (decorator.hasArgumentList()) {
          checkCallable(decorator, decorator);
        }
      }
    }

    private void checkCallable(@NotNull PyElement node, @Nullable PyExpression callee) {
      if (callee != null && isCallable(callee, myTypeEvalContext) == Boolean.FALSE) {
        final PyType calleeType = myTypeEvalContext.getType(callee);
        String message = "Expression is not callable";
        if (calleeType instanceof PyClassType) {
          message = String.format("'%s' object is not callable", calleeType.getName());
        }
        else {
          final String name = callee.getName();
          if (name != null) {
            message = String.format("'%s' is not callable", name);
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
