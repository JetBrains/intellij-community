package com.jetbrains.python.inspections;

import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.util.containers.Stack;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

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
      String name = node.getName();
      if (superClasses.length == 0 || (superClasses.length == 1 && PyNames.OBJECT.equals(superClasses[0].getText())))
        return;

      Stack<String> superNames = new Stack<String>();
      if (node.isNewStyleClass())
        superNames.push(PyNames.SUPER);

      addSuperNames(superNames, superClasses, name);

      if (!superHasConstructor(node)) return;
      PyFunction initMethod = node.findMethodByName(PyNames.INIT, false);
      if (initMethod != null) {
        if (hasConstructorCall(initMethod, superNames))
          return;
        registerProblem(initMethod.getNameIdentifier(), "Call to constructor of super class is missed");
      }
    }

    private static boolean superHasConstructor(PyClass node) {
      Stack<PyClass> st = new Stack<PyClass>();
      addSuperClasses(st, node.getSuperClasses(), node.getName());

      while (!st.empty()) {
        if ((st.pop()).findMethodByName(PyNames.INIT, false) != null) {
          return true;
        }
      }
      return false;
    }
    private static void addSuperClasses(Stack<PyClass> st, PyClass[] superClasses, String name) {
      for (PyClass cl : superClasses) {
        if (!name.equals(cl.getName())) {
          st.push(cl);
          addSuperClasses(st, cl.getSuperClasses(), name);
        }
      }
    }

    private static void addSuperNames(Stack<String> st, PsiElement[] superClasses, String name) {
      for (PsiElement cl : superClasses) {
        if (!name.equals(cl.getText())) {
          if (!PyNames.OBJECT.equals(cl.getText()))
            st.push(cl.getText());

          if (cl instanceof PyReferenceExpression) {
            PyReferenceExpression ref = (PyReferenceExpression) cl;
            final PsiElement result = ref.getReference(PyResolveContext.noProperties()).resolve();
            if (result instanceof PyClass)
              addSuperNames(st, ((PyClass)result).getSuperClassExpressions(), name);
          }
        }
      }
    }

    private static boolean hasConstructorCall(PyFunction initMethod, Stack<String> superNames) {
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

    private static boolean isConstructorCall(PyCallExpression expression, Stack<String> superNames) {
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
