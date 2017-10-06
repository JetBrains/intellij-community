// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.inspections;

import com.intellij.codeInspection.LocalInspectionToolSession;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.openapi.util.Condition;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.hash.HashMap;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.inspections.quickfix.PyRemoveAssignmentQuickFix;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.search.PyOverridingMethodsSearch;
import com.jetbrains.python.psi.types.PyNoneType;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.sdk.PySdkUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

/**
 * User: ktisha
 *
 * pylint E1111
 *
 * Used when an assignment is done on a function call but the inferred function doesn't return anything.
 */
public class PyNoneFunctionAssignmentInspection extends PyInspection {
  @Nls
  @NotNull
  @Override
  public String getDisplayName() {
    return PyBundle.message("INSP.NAME.none.function.assignment");
  }

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder,
                                        boolean isOnTheFly,
                                        @NotNull LocalInspectionToolSession session) {
    return new Visitor(holder, session);
  }


  private static class Visitor extends PyInspectionVisitor {
    private final Map<PyFunction, Boolean> myHasInheritors = new HashMap<>();

    public Visitor(@Nullable ProblemsHolder holder, @NotNull LocalInspectionToolSession session) {
      super(holder, session);
    }

    @Override
    public void visitPyAssignmentStatement(PyAssignmentStatement node) {
      final PyExpression value = node.getAssignedValue();
      if (value instanceof PyCallExpression) {
        final PyType type = myTypeEvalContext.getType(value);
        final PyCallExpression call = (PyCallExpression)value;
        final PyExpression callee = call.getCallee();

        if (type instanceof PyNoneType && callee != null) {
          final Condition<PyCallable> ignoredCallable = callable -> {
            if (PySdkUtil.isElementInSkeletons(callable)) {
              return true;
            }
            if (callable instanceof PyFunction) {
              final PyFunction function = (PyFunction)callable;
              // Currently we don't infer types returned by decorators
              if (hasInheritors(function) ||
                  PyKnownDecoratorUtil.hasUnknownOrChangingReturnTypeDecorator(function, myTypeEvalContext) ||
                  PyKnownDecoratorUtil.hasAbstractDecorator(function, myTypeEvalContext)) {
                return true;
              }
            }

            return false;
          };

          final List<PyCallable> callables = call.multiResolveCalleeFunction(getResolveContext());
          if (!callables.isEmpty() && !ContainerUtil.exists(callables, ignoredCallable)) {
            registerProblem(node, PyBundle.message("INSP.none.function.assignment", callee.getName()), new PyRemoveAssignmentQuickFix());
          }
        }
      }
    }

    private boolean hasInheritors(@NotNull PyFunction function) {
      final Boolean cached = myHasInheritors.get(function);
      if (cached != null) {
        return cached;
      }
      final boolean result = PyOverridingMethodsSearch.search(function, true).findFirst() != null;
      myHasInheritors.put(function, result);
      return result;
    }
  }
}
