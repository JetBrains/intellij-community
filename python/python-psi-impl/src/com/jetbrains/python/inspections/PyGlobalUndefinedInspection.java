// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.inspections;

import com.intellij.codeInspection.LocalInspectionToolSession;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.jetbrains.python.PyPsiBundle;
import com.jetbrains.python.psi.PyGlobalStatement;
import com.jetbrains.python.psi.PyTargetExpression;
import com.jetbrains.python.psi.PyUtil;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * User: ktisha
 * <p>
 * pylint W0601
 */
@ApiStatus.Internal
public final class PyGlobalUndefinedInspection extends PyInspection {

  @Override
  public @NotNull PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder,
                                                 boolean isOnTheFly,
                                                 @NotNull LocalInspectionToolSession session) {
    return new Visitor(holder, PyInspectionVisitor.getContext(session));
  }


  private static class Visitor extends PyInspectionVisitor {
    Visitor(@Nullable ProblemsHolder holder, @NotNull TypeEvalContext context) {
      super(holder, context);
    }

    @Override
    public void visitPyGlobalStatement(@NotNull PyGlobalStatement node) {
      final PyTargetExpression[] globals = node.getGlobals();

      for (PyTargetExpression global : globals) {
        final PyResolveContext resolveContext = PyResolveContext.defaultContext(myTypeEvalContext);

        final List<PsiElement> elements = PyUtil.multiResolveTopPriority(global.getReference(resolveContext));
        final boolean noTopLevelDeclaration = elements.stream().noneMatch(PyUtil::isTopLevel);

        if (noTopLevelDeclaration) {
          registerProblem(global, PyPsiBundle.problemMessage("INSP.global.variable.undefined", global.getName()));
        }
      }
    }
  }
}
