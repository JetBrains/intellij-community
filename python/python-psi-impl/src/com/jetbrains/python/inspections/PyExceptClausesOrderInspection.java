// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.inspections;

import com.intellij.codeInspection.LocalInspectionToolSession;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.jetbrains.python.PyPsiBundle;
import com.jetbrains.python.inspections.PyInspectionMessages.CodifiedParam;
import com.jetbrains.python.inspections.quickfix.PyMoveExceptQuickFix;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyExceptPart;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyReferenceExpression;
import com.jetbrains.python.psi.PyTryExceptStatement;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.Set;

@ApiStatus.Internal
public final class PyExceptClausesOrderInspection extends PyInspection {

  @Override
  public @NotNull PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder,
                                                 boolean isOnTheFly,
                                                 @NotNull LocalInspectionToolSession session) {
    return new Visitor(holder, PyInspectionVisitor.getContext(session));
  }

  private static class Visitor extends PyInspectionVisitor {

    Visitor(final ProblemsHolder holder, @NotNull TypeEvalContext context) {
      super(holder, context);
    }

    @Override
    public void visitPyTryExceptStatement(@NotNull PyTryExceptStatement node) {
      PyExceptPart[] exceptParts = node.getExceptParts();
      if (exceptParts.length > 1) {
        Set<PyClass> exceptClasses = new HashSet<>();
        for (PyExceptPart exceptPart : exceptParts) {
          PyExpression exceptClass = exceptPart.getExceptClass();
          if (exceptClass instanceof PyReferenceExpression) {
            PsiElement element = ((PyReferenceExpression)exceptClass).followAssignmentsChain(getResolveContext()).getElement();
            if (element instanceof PyClass pyClass) {
              if (exceptClasses.contains(pyClass)) {
                registerProblem(exceptClass, PyPsiBundle.problemMessage("INSP.bad.except.exception.class.already.caught",
                                                                        CodifiedParam.ofReference(pyClass)));
              }
              else {
                for (PyClass superClass : pyClass.getSuperClasses(null)) {
                  if (exceptClasses.contains(superClass)) {
                    registerProblem(exceptClass,
                                    PyPsiBundle.problemMessage("INSP.bad.except.superclass.of.exception.class.already.caught",
                                                               CodifiedParam.ofReference(superClass), CodifiedParam.ofReference(pyClass)),
                                    ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                                    new PyMoveExceptQuickFix());
                  }
                }
              }
              exceptClasses.add(pyClass);
            }
          }
        }
      }
    }
  }
}
