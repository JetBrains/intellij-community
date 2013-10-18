package com.jetbrains.python.inspections;

import com.intellij.codeInspection.LocalInspectionToolSession;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.inspections.quickfix.PyMakeFunctionFromMethodQuickFix;
import com.jetbrains.python.inspections.quickfix.PyMakeMethodStaticQuickFix;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.search.PyOverridingMethodsSearch;
import com.jetbrains.python.psi.search.PySuperMethodsSearch;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

/**
 * User: ktisha
 *
 */
public class PyMethodMayBeStaticInspection extends PyInspection {
  @Nls
  @NotNull
  @Override
  public String getDisplayName() {
    return PyBundle.message("INSP.NAME.method.may.be.static");
  }

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder,
                                        boolean isOnTheFly,
                                        @NotNull LocalInspectionToolSession session) {
    return new Visitor(holder, session);
  }


  private static class Visitor extends PyInspectionVisitor {
    public Visitor(@Nullable ProblemsHolder holder, @NotNull LocalInspectionToolSession session) {
      super(holder, session);
    }

    @Override
    public void visitPyFunction(PyFunction node) {
      if (PyNames.getBuiltinMethods(LanguageLevel.forElement(node)).containsKey(node.getName())) return;
      final PyClass containingClass = node.getContainingClass();
      if (containingClass == null) return;
      final Collection<PsiElement> supers = PySuperMethodsSearch.search(node).findAll();
      if (!supers.isEmpty()) return;
      final Collection<PyFunction> overrides = PyOverridingMethodsSearch.search(node, true).findAll();
      if (!overrides.isEmpty()) return;
      final PyDecoratorList decoratorList = node.getDecoratorList();
      if (decoratorList != null) {
        for (PyDecorator decorator : decoratorList.getDecorators()) {
          final String decoratorName = decorator.getName();
          if (hasSpecificDecorator(decoratorName)) {
            return;
          }
          final Property property = containingClass.findPropertyByCallable(node);
          if (property != null) return;
        }
      }

      final PyStatementList statementList = node.getStatementList();
      if (statementList == null) return;

      final PyStatement[] statements = statementList.getStatements();

      if (statements.length == 1 && statements[0] instanceof PyPassStatement) return;

      final PyParameter[] parameters = node.getParameterList().getParameters();

      final String selfName;
      if (parameters.length > 0) {
        final String name = parameters[0].getName();
        selfName = name != null ? name : parameters[0].getText();
      }
      else {
        selfName = PyNames.CANONICAL_SELF;
      }

      final boolean[] mayBeStatic = {true};
      PyRecursiveElementVisitor visitor = new PyRecursiveElementVisitor() {
        @Override
        public void visitPyRaiseStatement(PyRaiseStatement node) {
          super.visitPyRaiseStatement(node);
          final PyExpression[] expressions = node.getExpressions();
          if (expressions.length == 1) {
            final PyExpression expression = expressions[0];
            if (expression instanceof PyCallExpression) {
              final PyExpression callee = ((PyCallExpression)expression).getCallee();
              if (callee != null && PyNames.NOT_IMPLEMENTED_ERROR.equals(callee.getText()))
                mayBeStatic[0] = false;
            }
            else if (PyNames.NOT_IMPLEMENTED_ERROR.equals(expression.getText())) {
              mayBeStatic[0] = false;
            }
          }
        }

        @Override
        public void visitPyReferenceExpression(PyReferenceExpression node) {
          super.visitPyReferenceExpression(node);
          if (selfName.equals(node.getName())) {
            mayBeStatic[0] = false;
          }

        }

      };
      node.accept(visitor);
      final PsiElement identifier = node.getNameIdentifier();
      if (mayBeStatic[0] && identifier != null) {
        registerProblem(identifier, PyBundle.message("INSP.method.may.be.static"), ProblemHighlightType.WEAK_WARNING,
                        null, new PyMakeMethodStaticQuickFix(), new PyMakeFunctionFromMethodQuickFix());
      }
    }

    private static boolean hasSpecificDecorator(String decoratorName) {
      return PyNames.STATICMETHOD.equals(decoratorName) || PyNames.CLASSMETHOD.equals(decoratorName) ||
          PyNames.ABSTRACTMETHOD.equals(decoratorName) || PyNames.ABSTRACTPROPERTY.equals(decoratorName);
    }
  }
}
