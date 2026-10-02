// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.inspections;

import com.intellij.codeInspection.LocalInspectionToolSession;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElementVisitor;
import com.jetbrains.python.PyPsiBundle;
import com.jetbrains.python.inspections.quickfix.TransformClassicClassQuickFix;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public final class PyClassicStyleClassInspection extends PyInspection {

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
    public void visitPyClass(@NotNull PyClass node) {
      final ASTNode nameNode = node.getNameNode();
      if (!node.isNewStyleClass(myTypeEvalContext) && nameNode != null) {
        PyExpression[] superClassExpressions = node.getSuperClassExpressions();
        if (superClassExpressions.length == 0) {
          registerProblem(nameNode.getPsi(), PyPsiBundle.message("INSP.classic.class.usage.old.style.class"),
                          new TransformClassicClassQuickFix());
        }
        else {
          registerProblem(nameNode.getPsi(), PyPsiBundle.message("INSP.classic.class.usage.old.style.class.ancestors"));
        }
      }
    }
  }
}
