// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.inspections;

import com.intellij.codeInspection.LocalInspectionToolSession;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.PsiElementVisitor;
import com.jetbrains.python.PyPsiBundle;
import com.jetbrains.python.inspections.quickfix.RemoveDecoratorQuickFix;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyDecorator;
import com.jetbrains.python.psi.PyDecoratorList;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * User: catherine
 * <p>
 * Inspection to detect occurrences of @classmethod and @staticmethod
 * on methods outside of a class
 */
@ApiStatus.Internal
public final class PyDecoratorInspection extends PyInspection {

  @Override
  public @NotNull PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder,
                                                 boolean isOnTheFly,
                                                 @NotNull LocalInspectionToolSession session) {
    return new Visitor(holder, PyInspectionVisitor.getContext(session));
  }

  private static class Visitor extends PyInspectionVisitor {
    Visitor(@Nullable ProblemsHolder holder,
            @NotNull TypeEvalContext context) {
      super(holder, context);
    }

    @Override
    public void visitPyFunction(final @NotNull PyFunction node) {
      PyClass containingClass = node.getContainingClass();
      if (containingClass != null) {
        return;
      }

      PyDecoratorList decorators = node.getDecoratorList();
      if (decorators == null) {
        return;
      }
      for (PyDecorator decorator : decorators.getDecorators()) {
        String name = decorator.getText();
        if (name.equals("@classmethod") || name.equals("@staticmethod")) {
          registerProblem(decorator, PyPsiBundle.message("INSP.decorators.method.only.decorator.on.method.outside.class", name),
                          new RemoveDecoratorQuickFix());
        }
      }
    }
  }
}
