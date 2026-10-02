// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.inspections;

import com.intellij.codeInspection.LocalInspectionToolSession;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.jetbrains.python.PyPsiBundle;
import com.jetbrains.python.inspections.quickfix.PyCreatePropertyQuickFix;
import com.jetbrains.python.psi.AccessDirection;
import com.jetbrains.python.psi.Property;
import com.jetbrains.python.psi.PyAugAssignmentStatement;
import com.jetbrains.python.psi.PyCallable;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyQualifiedExpression;
import com.jetbrains.python.psi.PyReferenceExpression;
import com.jetbrains.python.psi.PyTargetExpression;
import com.jetbrains.python.psi.types.PyClassType;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import com.jetbrains.python.toolbox.Maybe;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * Checks that properties are accessed correctly.
 */
@ApiStatus.Internal
public final class PyPropertyAccessInspection extends PyInspection {

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

    Visitor(@NotNull ProblemsHolder holder, @NotNull TypeEvalContext context) {
      super(holder, context);
    }

    @Override
    public void visitPyReferenceExpression(@NotNull PyReferenceExpression node) {
      super.visitPyReferenceExpression(node);
      checkPropertyExpression(node);
    }

    @Override
    public void visitPyTargetExpression(@NotNull PyTargetExpression node) {
      super.visitPyTargetExpression(node);
      checkPropertyExpression(node);
    }

    private void checkPropertyExpression(PyQualifiedExpression node) {
      final PyExpression qualifier = node.getQualifier();
      if (qualifier != null) {
        final PyType type = myTypeEvalContext.getType(qualifier);
        if (type instanceof PyClassType) {
          final PyClass cls = ((PyClassType)type).getPyClass();
          final String name = node.getName();
          if (name != null) {
            final Property property = cls.findProperty(name, true, myTypeEvalContext);
            if (property != null) {
              final AccessDirection dir = AccessDirection.of(node);
              checkAccessor(node, name, dir, property);
              if (dir == AccessDirection.READ) {
                final PsiElement parent = node.getParent();
                if (parent instanceof PyAugAssignmentStatement && ((PyAugAssignmentStatement)parent).getTarget() == node) {
                  checkAccessor(node, name, AccessDirection.WRITE, property);
                }
              }
            }
          }
        }
      }
    }

    private void checkAccessor(@NotNull PyExpression node,
                               @NotNull String name,
                               @NotNull AccessDirection dir,
                               @NotNull Property property) {
      final Maybe<PyCallable> accessor = property.getByDirection(dir);
      if (accessor.isDefined() && accessor.value() == null) {
        final PyInspectionMessages.ProblemMessage message;
        if (dir == AccessDirection.WRITE) {
          message = PyPsiBundle.problemMessage("INSP.property.cannot.be.set", name);
        }
        else if (dir == AccessDirection.DELETE) {
          message = PyPsiBundle.problemMessage("INSP.property.cannot.be.deleted", name);
        }
        else {
          message = PyPsiBundle.problemMessage("INSP.property.cannot.be.read", name);
        }
        registerProblem(node, message, ProblemHighlightType.GENERIC_ERROR_OR_WARNING, new PyCreatePropertyQuickFix(dir));
      }
    }
  }
}
