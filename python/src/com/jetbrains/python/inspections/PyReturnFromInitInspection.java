package com.jetbrains.python.inspections;

import com.intellij.codeInspection.LocalInspectionToolSession;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyReturnStatement;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;

/**
 * Checks that no value is returned from __init__().
 * User: dcheryasov
 * Date: Nov 12, 2009 10:20:49 PM
 */
public class PyReturnFromInitInspection extends PyInspection {
  @Nls
  @NotNull
  public String getDisplayName() {
    return PyBundle.message("INSP.NAME.init.return");
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

    public void visitPyFunction(PyFunction function) {
      if (function.getContainingClass() != null && PyNames.INIT.equals(function.getName())) {
        Collection<PsiElement> offenders = new ArrayList<PsiElement>();
        findReturnValueInside(function, offenders);
        for (PsiElement offender : offenders) {
          registerProblem(offender, PyBundle.message("INSP.cant.return.value.from.init"));
        }
      }
    }

    private static void findReturnValueInside(@NotNull PsiElement node, Collection<PsiElement> offenders) {
      for (PsiElement child = node.getFirstChild(); child != null; child = child.getNextSibling()) {
        if (child instanceof PyFunction || child instanceof PyClass) continue; // ignore possible inner functions and classes
        if (child instanceof PyReturnStatement) {
          if (((PyReturnStatement)child).getExpression() != null) offenders.add(child);
        }
        findReturnValueInside(child, offenders);
      }
    }
  }
}
