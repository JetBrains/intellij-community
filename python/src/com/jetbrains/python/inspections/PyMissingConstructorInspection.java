package com.jetbrains.python.inspections;

import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiReference;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.actions.AddCallSuperQuickFix;
import com.jetbrains.python.psi.*;
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
      if (superClasses.length == 0 || (superClasses.length == 1 && PyNames.OBJECT.equals(superClasses[0].getText())))
        return;

      if (!superHasConstructor(node)) return;
      PyFunction initMethod = node.findMethodByName(PyNames.INIT, false);
      if (initMethod != null) {
        if (hasConstructorCall(node, initMethod))
          return;
        if (superClasses.length == 1 || node.isNewStyleClass())
          registerProblem(initMethod.getNameIdentifier(), "Call to constructor of super class is missed",
                          new AddCallSuperQuickFix(node.getSuperClasses()[0], superClasses[0].getText()));
        else
          registerProblem(initMethod.getNameIdentifier(), "Call to constructor of super class is missed");
      }
    }

    private static boolean superHasConstructor(PyClass node) {
      for (PyClass s : node.iterateAncestorClasses()) {
        if (!PyNames.OBJECT.equals(s.getName()) && !PyNames.FAKE_OLD_BASE.equals(s.getName()) &&
            node.getName() != null && !node.getName().equals(s.getName())
            && s.findMethodByName(PyNames.INIT, false) != null) {
          return true;
        }
      }
      return false;
    }

    private static boolean hasConstructorCall(PyClass node, PyFunction initMethod) {
      PyStatementList statementList = initMethod.getStatementList();
      CallVisitor visitor = new CallVisitor(node);
      if (statementList != null) {
        statementList.accept(visitor);
        return visitor.myHasConstructorCall;
      }
      return false;
    }

    private static class CallVisitor extends PyRecursiveElementVisitor {
      private boolean myHasConstructorCall = false;
      private PyClass myClass;
      CallVisitor(PyClass node) {
        myClass = node;
      }

      @Override
      public void visitPyCallExpression(PyCallExpression node) {
        if (isConstructorCall(node, myClass))
          myHasConstructorCall = true;
      }

      private static boolean isConstructorCall(PyCallExpression expression, PyClass cl) {
        PyExpression callee = expression.getCallee();
        if (callee instanceof PyQualifiedExpression) {
          PyExpression qualifier = ((PyQualifiedExpression)callee).getQualifier();
          if (qualifier != null) {
            String tmp = "";
            PsiElement callingClass = null;
            if (qualifier instanceof PyCallExpression) {
              PyExpression innerCallee = ((PyCallExpression)qualifier).getCallee();
              if (innerCallee != null) {
                tmp = innerCallee.getName();
                PsiReference ref = innerCallee.getReference();
                if (ref != null)
                  callingClass = ref.resolve();
              }
              if (PyNames.SUPER.equals(tmp) && (PyNames.INIT.equals(callee.getName()))) {
                PyExpression[] args = ((PyCallExpression)qualifier).getArguments();
                if (args.length > 0) {
                  if (args[0].getText().equals(cl.getName()))
                      return true;
                  for (PyClass s : cl.iterateAncestorClasses()) {
                    if (args[0].getText().equals(s.getName()))
                      return true;
                  }
                }
                else
                  return true;
              }
            }
            else {
              PsiReference ref = qualifier.getReference();
              if (ref != null)
                callingClass = ref.resolve();
            }
            for (PyClass s : cl.iterateAncestorClasses()) {
              if (s.equals(callingClass)) {
                if(PyNames.INIT.equals(callee.getName())){
                  return true;
                }
              }
            }
          }
        }
        return false;
      }
    }
  }
}
