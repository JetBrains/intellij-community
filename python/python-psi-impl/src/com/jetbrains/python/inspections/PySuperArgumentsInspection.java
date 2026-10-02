// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.inspections;

import com.intellij.codeInspection.LocalInspectionToolSession;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.PyPsiBundle;
import com.jetbrains.python.inspections.PyInspectionMessages.CodifiedParam;
import com.jetbrains.python.psi.PyCallExpression;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyReferenceExpression;
import com.jetbrains.python.psi.types.PyClassType;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public final class PySuperArgumentsInspection extends PyInspection {

  @Override
  public @NotNull PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder,
                                                 boolean isOnTheFly,
                                                 @NotNull LocalInspectionToolSession session) {
    TypeEvalContext context = PyInspectionVisitor.getContext(session);
    if (context.getUsesExternalTypeEngine()) {
      return PsiElementVisitor.EMPTY_VISITOR;
    }
    return new Visitor(holder, context);
  }

  private static class Visitor extends PyInspectionVisitor {

    Visitor(final ProblemsHolder holder, @NotNull TypeEvalContext context) {
      super(holder, context);
    }

    @Override
    public void visitPyCallExpression(@NotNull PyCallExpression node) {
      final PyExpression callee = node.getCallee();
      if (callee != null) {
        if (PyNames.SUPER.equals(callee.getName())) {
          PyExpression[] arguments = node.getArguments();
          if (arguments.length == 2) {
            if (arguments[0] instanceof PyReferenceExpression && arguments[1] instanceof PyReferenceExpression) {
              PyClass firstClass = findClassOf(arguments[0]);
              PyClass secondClass = findClassOf(arguments[1]);
              if (firstClass != null && secondClass != null) {
                if (!secondClass.isSubclass(firstClass, myTypeEvalContext)) {
                  registerProblem(
                    node.getArgumentList(),
                    PyPsiBundle.problemMessage("INSP.class.is.not.subtype.of.class",
                                               CodifiedParam.ofReference(secondClass), CodifiedParam.ofReference(firstClass))
                  );
                }
              }
            }
          }
        }
      }
    }

    private @Nullable PyClass findClassOf(PyExpression argument) {
      PsiElement firstElement = ((PyReferenceExpression)argument).followAssignmentsChain(getResolveContext()).getElement();
      PyClass firstClass = null;
      if (firstElement instanceof PyClass) {
        firstClass = (PyClass)firstElement;
      }
      else if (firstElement instanceof PyExpression) {
        PyType first_type = myTypeEvalContext.getType((PyExpression)firstElement);
        if (first_type instanceof PyClassType) {
          firstClass = ((PyClassType)first_type).getPyClass();
        }
      }
      return firstClass;
    }
  }
}
