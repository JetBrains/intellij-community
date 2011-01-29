package com.jetbrains.python.inspections;

import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.PsiElementVisitor;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.psi.PyDecorator;
import com.jetbrains.python.psi.PyDecoratorList;
import com.jetbrains.python.psi.PyFunction;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

/**
 * Checks nested decorators, especially whatever comes after @classmethod.
 * <br/>
 * User: dcheryasov
 * Date: Sep 4, 2010 3:56:57 AM
 */
public class PyNestedDecoratorsInspection extends PyInspection {
  @Nls
  @NotNull
  public String getDisplayName() {
    return PyBundle.message("INSP.NAME.nested.decorators");
  }

  @NotNull
  public HighlightDisplayLevel getDefaultLevel() {
    return HighlightDisplayLevel.WEAK_WARNING;
  }

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull final ProblemsHolder holder, final boolean isOnTheFly) {
    return new Visitor(holder);
  }

  public static class Visitor extends PyInspectionVisitor {

    public Visitor(final ProblemsHolder holder) {
      super(holder);
    }

    @Override
    public void visitPyFunction(final PyFunction node) {
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
                PyBundle.message("INSP.decorator.receives.unexpected.builtin"), 
                ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                null
              );
            }
          }
        }
      }
    }
  }
}
