package com.jetbrains.python.inspections;

import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.PsiElementVisitor;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.actions.RemoveDecoratorQuickFix;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyDecorator;
import com.jetbrains.python.psi.PyDecoratorList;
import com.jetbrains.python.psi.PyFunction;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

/**
 * User: catherine
 *
 * Inspection to detect occurrences of @classmethod and @staticmethod
 * on methods outside of a class
 */
public class PyDecoratorInspection extends PyInspection {
  @Nls
  @NotNull
  @Override
  public String getDisplayName() {
    return PyBundle.message("INSP.NAME.decorator.outside.class");
  }

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    return new Visitor(holder);
  }

  private static class Visitor extends PyInspectionVisitor {

    public Visitor(final ProblemsHolder holder) {
      super(holder);
    }

    @Override
    public void visitPyFunction(final PyFunction node){
      PyClass containingClass = node.getContainingClass();
      if (containingClass != null)
        return;

      PyDecoratorList decorators = node.getDecoratorList();
      if (decorators == null)
        return;
      for (PyDecorator decorator : decorators.getDecorators()) {
        String name = decorator.getText();
        if (name.equals("@classmethod") || name.equals("@staticmethod"))
          registerProblem(decorator, "Decorator " + name + " on method outside class", new RemoveDecoratorQuickFix());
      }
    }
  }
}
