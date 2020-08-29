// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.inspections;

import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.codeInspection.LocalInspectionToolSession;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.PsiElementVisitor;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.PyPsiBundle;
import com.jetbrains.python.inspections.quickfix.RemoveDecoratorQuickFix;
import com.jetbrains.python.psi.PyDecorator;
import com.jetbrains.python.psi.PyDecoratorList;
import com.jetbrains.python.psi.PyFunction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Checks nested decorators, especially whatever comes after @classmethod.
 * <br/>
 * User: dcheryasov
 */
public class PyNestedDecoratorsInspection extends PyInspection {

  @Override
  @NotNull
  public HighlightDisplayLevel getDefaultLevel() {
    return HighlightDisplayLevel.WEAK_WARNING;
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
    public void visitPyFunction(final @NotNull PyFunction node) {
      PyDecoratorList decolist = node.getDecoratorList();
      if (decolist != null) {
        PyDecorator[] decos = decolist.getDecorators();
        if (decos.length > 1) {
          for (int i = decos.length - 1; i >= 1; i -= 1) {
            PyDecorator deco = decos[i];
            String deconame = deco.getName();
            if ((PyNames.CLASSMETHOD.equals(deconame) || PyNames.STATICMETHOD.equals(deconame)) && deco.isBuiltin()) {
              registerProblem(
                decos[i-1],
                PyPsiBundle.message("INSP.decorator.receives.unexpected.builtin"),
                ProblemHighlightType.GENERIC_ERROR_OR_WARNING, null, new RemoveDecoratorQuickFix()
              );
            }
          }
        }
      }
    }
  }
}
