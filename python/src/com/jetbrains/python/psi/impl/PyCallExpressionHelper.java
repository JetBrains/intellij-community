package com.jetbrains.python.psi.impl;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiPolyVariantReference;
import com.intellij.psi.PsiReference;
import com.intellij.psi.ResolveResult;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.PythonLanguage;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.types.PyClassType;
import com.jetbrains.python.psi.types.PyDecorator;
import com.jetbrains.python.psi.types.PyType;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;

/**
 * Functions common to different implementors of PyCallExpression, with different base classes.
 * User: dcheryasov
 * Date: Dec 23, 2008 10:31:38 AM
 */
public class PyCallExpressionHelper {
  private PyCallExpressionHelper() {
    // none
  }

  public static PyArgumentList getArgumentList(PyElement us) {
    PyArgumentList arglist = PsiTreeUtil.getChildOfType(us, PyArgumentList.class);
    return arglist;
  }

  public static void addArgument(PyCallExpression us, PythonLanguage language, PyExpression expression) {
    PyExpression[] arguments = us.getArgumentList().getArguments();
    try {
      language.getElementGenerator()
        .insertItemIntoList(us.getProject(), us, arguments.length == 0 ? null : arguments[arguments.length - 1], expression);
    }
    catch (IncorrectOperationException e1) {
      throw new IllegalArgumentException(e1);
    }
  }

  @Nullable
  public static PyCallExpression.PyMarkedFunction resolveCallee(PyCallExpression us) {
    PyExpression calleeReference = us.getCallee();
    if (calleeReference != null) {
      PsiReference cref = calleeReference.getReference();
      if (cref != null) {
        // paranoia: multi-resolve or not? must get a function out of it
        PsiElement resolved = null;
        if (cref instanceof PsiPolyVariantReference) {
          PsiPolyVariantReference poly = (PsiPolyVariantReference)cref;
          ResolveResult[] targets = poly.multiResolve(false);
          for (ResolveResult target : targets) {
            PsiElement elt = target.getElement();
            if (elt instanceof PyFunction) {
              resolved = elt;
              break;
            }
          }
        }
        else resolved = cref.resolve();
        if (resolved != null) {
          EnumSet<PyCallExpression.Flag> flags = EnumSet.noneOf(PyCallExpression.Flag.class);
          //boolean is_inst = isByInstance();
          if (isByInstance(us)) flags.add(PyCallExpression.Flag.IMPLICIT_FIRST_ARG);
          if (resolved instanceof PyFunction) {
            PyFunction meth = (PyFunction)resolved; // constructor call?
            if (PyNames.INIT.equals(meth.getName())) flags.add(PyCallExpression.Flag.IMPLICIT_FIRST_ARG);
            // look for closest decorator
            PyDecoratorList decolist = meth.getDecoratorList();
            if (decolist != null) {
              PyDecorator[] decos = decolist.getDecorators();
              // TODO: look for all decorators
              if (decos.length == 1) {
                PyDecorator deco = decos[0];
                String deconame = deco.getName();
                if (deco.isBuiltin()) {
                  if (PyNames.STATICMETHOD.equals(deconame)) {
                    flags.add(PyCallExpression.Flag.STATICMETHOD);
                    flags.remove(PyCallExpression.Flag.IMPLICIT_FIRST_ARG);
                  }
                  else if (PyNames.CLASSMETHOD.equals(deconame)) {
                    flags.add(PyCallExpression.Flag.CLASSMETHOD);
                  }
                  // else could be custom decorator processing
                }
              }
            }
          }
          if (!(resolved instanceof PyFunction)) return null; // omg, bogus __init__
          return new PyCallExpression.PyMarkedFunction((PyFunction) resolved, flags);
        }
      }
    }
    return null;
  }

  @Nullable
  public static PyElement resolveCallee2(PyCallExpression us) {
    PyExpression calleeReference = us.getCallee();
    final PsiReference ref = calleeReference.getReference();
    if (ref == null) return null;
    return (PyElement) ref.resolve();
  }

  protected static boolean isByInstance(PyCallExpression us) {
    PyExpression callee = us.getCallee();
    if (callee instanceof PyReferenceExpression) {
      PyExpression qualifier = ((PyReferenceExpression)callee).getQualifier();
      if (qualifier != null) {
        PyType type = qualifier.getType();
        if ((type instanceof PyClassType) && (!((PyClassType)type).isDefinition())) {
          // we're calling an instance method of qualifier
          return true;
        }
      }
    }
    return false;
  }

}
