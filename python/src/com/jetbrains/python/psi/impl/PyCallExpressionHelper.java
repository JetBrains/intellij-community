package com.jetbrains.python.psi.impl;

import com.intellij.openapi.util.Pair;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.PythonLanguage;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.types.PyClassType;
import com.jetbrains.python.psi.PyDecorator;
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

  /**
   * Tries to interpret a call as a call to built-in {@code classmethod} or {@code staticmethod}.
   * @param redefining_call the possible call, generally a result of chasing a chain of assignments
   * @param us any in-project PSI element, used to determine SDK and ultimately builtins module used to check the wrapping functions
   * @return a pair of wrapper name and wrapped function; for {@code staticmethod(foo)} it would be ("staticmethod", foo).
   */
  @Nullable
  public static Pair<String, PyFunction> interpretAsStaticmethodOrClassmethodWrappingCall(PyCallExpression redefining_call, PsiElement us) {
    PyExpression redefining_callee = redefining_call.getCallee();
    if (redefining_callee instanceof PyReferenceExpression) {
      final PyReferenceExpression refex = (PyReferenceExpression)redefining_callee;
      final String refname = refex.getReferencedName();
      if ((PyNames.CLASSMETHOD.equals(refname) || PyNames.STATICMETHOD.equals(refname))) {
        PsiElement redefining_func = refex.resolve();
        if (redefining_func != null) {
          PsiNamedElement true_func = PyBuiltinCache.getInstance(us).getByName(refname, PsiNamedElement.class);
          if (true_func instanceof PyClass) true_func = ((PyClass)true_func).findMethodByName(PyNames.INIT, true);
          if (true_func == redefining_func) {
            // yes, really a case of "foo = classmethod(foo)"
            PyArgumentList arglist = redefining_call.getArgumentList();
            if (arglist != null) { // really can't be any other way
              PyExpression[] args = arglist.getArguments();
              if (args.length == 1) {
                PyExpression possible_original_ref = args[0];
                if (possible_original_ref instanceof PyReferenceExpression) {
                  PsiElement original = ((PyReferenceExpression)possible_original_ref).resolve();
                  if (original instanceof PyFunction) {
                    // pinned down the original; replace our resolved callee with it and add flags.
                    return new Pair<String, PyFunction>(refname, (PyFunction)original);
                  }
                }
              }
            }
          }
        }
      }
    }
    return null;
  }

  @Nullable
  public static PyCallExpression.PyMarkedFunction resolveCallee(PyCallExpression us) {
    PyExpression callee = us.getCallee();
    PyFunction.Flag wrapped_flag = null;
    if (callee instanceof PyReferenceExpression) {
      PyReferenceExpression ref = (PyReferenceExpression)callee;
      PsiElement resolved = ref.followAssignmentsChain();
      if (resolved instanceof PyClass) resolved = ((PyClass)resolved).findMethodByName(PyNames.INIT, false); // class to constructor call
      else if (resolved instanceof PyCallExpression) {
        // is it a case of "foo = classmethod(foo)"?
        PyCallExpression redefining_call = (PyCallExpression)resolved;
        Pair<String, PyFunction> wrapper_info = interpretAsStaticmethodOrClassmethodWrappingCall(redefining_call, us);
        if (wrapper_info != null) {
          resolved = wrapper_info.getSecond();
          String wrapper_name = wrapper_info.getFirst();
          if (PyNames.CLASSMETHOD.equals(wrapper_name)) wrapped_flag = PyFunction.Flag.CLASSMETHOD;
          else if (PyNames.STATICMETHOD.equals(wrapper_name)) wrapped_flag = PyFunction.Flag.STATICMETHOD;
        }
      }
      if (resolved instanceof PyFunction) {
        EnumSet<PyFunction.Flag> flags = EnumSet.noneOf(PyFunction.Flag.class);
        int implicit_offset = 0;
        boolean is_by_instance = isByInstance(us);
        if (is_by_instance) implicit_offset += 1;
        // wrapped flags?
        if (wrapped_flag != null) {
          flags.add(wrapped_flag);
          flags.add(PyFunction.Flag.WRAPPED);
          if (wrapped_flag == PyFunction.Flag.STATICMETHOD && implicit_offset > 0) implicit_offset -= 1; // might have marked it as implicit 'self'
          if (wrapped_flag == PyFunction.Flag.CLASSMETHOD && ! is_by_instance) implicit_offset += 1; // Both Foo.method() and foo.method() have implicit the first arg
        }
        // decorators?
        PyFunction method = (PyFunction)resolved; // constructor call?
        if (PyNames.INIT.equals(method.getName())) implicit_offset += 1;
        // look for closest decorator
        PyDecoratorList decolist = method.getDecoratorList();
        if (decolist != null) {
          PyDecorator[] decos = decolist.getDecorators();
          // TODO: look for all decorators
          if (decos.length == 1) {
            PyDecorator deco = decos[0];
            String deconame = deco.getName();
            if (deco.isBuiltin()) {
              if (PyNames.STATICMETHOD.equals(deconame)) {
                flags.add(PyFunction.Flag.STATICMETHOD);
                if (implicit_offset > 0) implicit_offset -= 1; // might have marked it as implicit 'self'
              }
              else if (PyNames.CLASSMETHOD.equals(deconame)) {
                flags.add(PyFunction.Flag.CLASSMETHOD);
                if (! is_by_instance) implicit_offset += 1; // Both Foo.method() and foo.method() have implicit the first arg
              }
              // else could be custom decorator processing
            }
          }
        }
        return new PyCallExpression.PyMarkedFunction((PyFunction)resolved, flags, implicit_offset);
      }
    }
    return null;
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
