package com.jetbrains.python.inspections;

import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.util.containers.Stack;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.psi.*;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.Set;

/**
 * User: catherine
 *
 * Inspection to warn if call to super constructor in class is missed
 */
public class PyMissingConstructorInspection extends PyInspection {
  @Nls
  @NotNull
  @Override
  public String getDisplayName() {
    return PyBundle.message("INSP.NAME.missing.super.constructor");
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
    public void visitPyClass(final PyClass node) {
      PsiElement[] superClasses = node.getSuperClassExpressions();
      if (superClasses.length == 0)
        return;
      Set<String> superNames = new HashSet<String>();
      if (node.isNewStyleClass())
        superNames.add(PyNames.SUPER);
      for (PsiElement cl : superClasses) {
        if (!PyNames.OBJECT.equals(cl.getText()))
          superNames.add(cl.getText());
      }
      if (superClasses.length == 1 && PyNames.OBJECT.equals(superClasses[0].getText()))
        return;

      PyFunction initMethod = node.findMethodByName(PyNames.INIT, false);
      if (initMethod != null) {
        if (hasConstructorCall(initMethod, superNames))
          return;
        registerProblem(initMethod.getNameIdentifier(), "Call to constructor of super class is missed");
      }
    }

    private static boolean hasConstructorCall(PyFunction initMethod, Set<String> superNames) {
      Stack<PsiElement> stack = new Stack<PsiElement>();
      PyStatementList statementList = initMethod.getStatementList();
      boolean hasConstructor = false;
      if (statementList != null) {
        for (PyStatement st : statementList.getStatements()) {
          stack.push(st);
          while (!stack.isEmpty()) {
            PsiElement e = stack.pop();
            if (e instanceof PyExpressionStatement) {
              PyExpression expression = ((PyExpressionStatement)e).getExpression();
              if (expression instanceof PyCallExpression) {
                if (isConstructorCall((PyCallExpression)expression, superNames))
                  hasConstructor = true;
              }
            }
            for (PsiElement psiElement : e.getChildren()) {
              stack.push(psiElement);
            }
          }
        }
      }
      return hasConstructor;
    }

    private static boolean isConstructorCall(PyCallExpression expression, Set<String> superNames) {
      PyExpression callee = expression.getCallee();
      if (callee instanceof PyQualifiedExpression) {
        PyExpression qualifier = ((PyQualifiedExpression)callee).getQualifier();
        if (qualifier != null) {
          String tmp = "";
          if (qualifier instanceof PyCallExpression) {
            PyExpression innerCallee = ((PyCallExpression)qualifier).getCallee();
            if (innerCallee != null)
              tmp = innerCallee.getName();
          }
          else
            tmp = qualifier.getText();
          if (superNames.contains(tmp)) {
            if(PyNames.INIT.equals(callee.getName())){
              return true;
            }
          }
        }
      }
      return false;
    }
  }
}
