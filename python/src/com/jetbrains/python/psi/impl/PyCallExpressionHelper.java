package com.jetbrains.python.psi.impl;

import com.intellij.openapi.util.Pair;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNamedElement;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.resolve.QualifiedResolveResult;
import com.jetbrains.python.psi.types.PyClassType;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
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

  public static void addArgument(PyCallExpression us, PyExpression expression) {
    PyExpression[] arguments = us.getArgumentList().getArguments();
    PyElementGenerator.getInstance(us.getProject()).insertItemIntoList(us,
                                                                       arguments.length == 0 ? null : arguments[arguments.length - 1],
                                                                       expression);
  }

  /**
   * Tries to interpret a call as a call to built-in {@code classmethod} or {@code staticmethod}.
   *
   * @param redefining_call the possible call, generally a result of chasing a chain of assignments
   * @param us              any in-project PSI element, used to determine SDK and ultimately builtins module used to check the wrapping functions
   * @return a pair of wrapper name and wrapped function; for {@code staticmethod(foo)} it would be ("staticmethod", foo).
   */
  @Nullable
  public static Pair<String, PyFunction> interpretAsStaticmethodOrClassmethodWrappingCall(PyCallExpression redefining_call, PsiElement us) {
    PyExpression redefining_callee = redefining_call.getCallee();
    if (redefining_callee instanceof PyReferenceExpression) {
      final PyReferenceExpression refex = (PyReferenceExpression)redefining_callee;
      final String refname = refex.getReferencedName();
      if ((PyNames.CLASSMETHOD.equals(refname) || PyNames.STATICMETHOD.equals(refname))) {
        PsiElement redefining_func = refex.getReference().resolve();
        if (redefining_func != null) {
          PsiNamedElement true_func = PyBuiltinCache.getInstance(us).getByName(refname, PsiNamedElement.class);
          if (true_func instanceof PyClass) true_func = ((PyClass)true_func).findInitOrNew(true);
          if (true_func == redefining_func) {
            // yes, really a case of "foo = classmethod(foo)"
            PyArgumentList arglist = redefining_call.getArgumentList();
            if (arglist != null) { // really can't be any other way
              PyExpression[] args = arglist.getArguments();
              if (args.length == 1) {
                PyExpression possible_original_ref = args[0];
                if (possible_original_ref instanceof PyReferenceExpression) {
                  PsiElement original = ((PyReferenceExpression)possible_original_ref).getReference().resolve();
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
  public static PyClass resolveCalleeClass(PyCallExpression us) {
    PyExpression callee = us.getCallee();

    PsiElement resolved;
    QualifiedResolveResult resolveResult = null;
    if (callee instanceof PyReferenceExpression) {
      // dereference
      PyReferenceExpression ref = (PyReferenceExpression)callee;
      resolveResult = ref.followAssignmentsChain();
      resolved = resolveResult.getElement();
    }
    else {
      resolved = callee;
    }
    // analyze
    if (resolved instanceof PyClass) {
      return (PyClass)resolved;
    }
    else if (resolved instanceof PyFunction) {
        final PyFunction pyFunction = (PyFunction) resolved;
        return pyFunction.getContainingClass();
    }

    return null;
  }

  @Nullable
  public static PyCallExpression.PyMarkedCallee resolveCallee(PyCallExpression us) {
    PyFunction.Flag wrappedFlag = null;
    boolean isConstructorCall = false;

    PyExpression callee = us.getCallee();
    PsiElement resolved;
    QualifiedResolveResult resolveResult = null;
    if (callee instanceof PyReferenceExpression) {
      // dereference
      PyReferenceExpression ref = (PyReferenceExpression)callee;
      resolveResult = ref.followAssignmentsChain();
      resolved = resolveResult.getElement();
    }
    else {
      resolved = callee;
    }
    // analyze
    if (resolved instanceof PyClass) {
      resolved = ((PyClass)resolved).findInitOrNew(true); // class to constructor call
      isConstructorCall = true;
    }
    else if (resolved instanceof PyCallExpression) {
      // is it a case of "foo = classmethod(foo)"?
      PyCallExpression redefiningCall = (PyCallExpression)resolved;
      Pair<String, PyFunction> wrapperInfo = interpretAsStaticmethodOrClassmethodWrappingCall(redefiningCall, us);
      if (wrapperInfo != null) {
        resolved = wrapperInfo.getSecond();
        String wrapper_name = wrapperInfo.getFirst();
        if (PyNames.CLASSMETHOD.equals(wrapper_name)) {
          wrappedFlag = PyFunction.Flag.CLASSMETHOD;
        }
        else if (PyNames.STATICMETHOD.equals(wrapper_name)) wrappedFlag = PyFunction.Flag.STATICMETHOD;
      }
    }
    if (resolved instanceof Callable) {
      EnumSet<PyFunction.Flag> flags = EnumSet.noneOf(PyFunction.Flag.class);
      PyExpression lastQualifier = resolveResult != null ? resolveResult.getLastQualifier() : null;
      final PyExpression callReference = us.getCallee();
      boolean is_by_instance = isByInstance(callReference);
      if (lastQualifier != null) {
        PyType qualifier_type = lastQualifier.getType(TypeEvalContext.fast()); // NOTE: ...or slow()?
        is_by_instance |=
          (qualifier_type != null && qualifier_type instanceof PyClassType && !((PyClassType)qualifier_type).isDefinition());
      }
      final Callable callable = (Callable)resolved;
      int implicitOffset = getImplicitArgumentCount(callReference, callable, wrappedFlag, flags, is_by_instance);
      if (!isConstructorCall && PyNames.NEW.equals(callable.getName())) {
        implicitOffset = Math.min(implicitOffset - 1, 0); // case of Class.__new__
      }
      return new PyCallExpression.PyMarkedCallee(callable, flags, implicitOffset,
                                                 resolveResult != null ? resolveResult.isImplicit() : false);
    }
    return null;
  }

  /**
   * Calls the {@link #getImplicitArgumentCount(PyExpression, Callable, PyFunction.Flag, EnumSet<PyFunction.Flag>, boolean) full version}
   * with null flags and with isByInstance inferred directly from call site (won't work with reassigned bound methods).
   *
   * @param callReference       the call site, where arguments are given.
   * @param functionBeingCalled resolved method which is being called; plain functions are OK but make little sense.
   * @return a non-negative number of parameters that are implicit to this call.
   */
  public static int getImplicitArgumentCount(final PyExpression callReference, PyFunction functionBeingCalled) {
    return getImplicitArgumentCount(callReference, functionBeingCalled, null, null, isByInstance(callReference));
  }

  /**
   * Finds how many arguments are implicit in a given call.
   *
   * @param callReference the call site, where arguments are given.
   * @param callable      resolved method which is being called; other callables are OK but immediately return 0.
   * @param wrappedFlag   value of {@link PyFunction.Flag#WRAPPED} if known.
   * @param flags         set of flags to be <i>updated</i> by this call; wrappedFlag's value ends up here, too.
   * @param isByInstance  true if the call is known to be by instance (not by class).
   * @return a non-negative number of parameters that are implicit to this call. E.g. for a typical method call 1 is returned
   *         because one parameter ('self') is implicit.
   */
  private static int getImplicitArgumentCount(final PyExpression callReference,
                                              Callable callable,
                                              @Nullable PyFunction.Flag wrappedFlag,
                                              @Nullable EnumSet<PyFunction.Flag> flags,
                                              boolean isByInstance
  ) {
    int implicit_offset = 0;
    PyFunction method = callable.asMethod();
    if (method == null) return implicit_offset;
    //
    if (isByInstance) implicit_offset += 1;
    // wrapped flags?
    if (wrappedFlag != null) {
      if (flags != null) {
        flags.add(wrappedFlag);
        flags.add(PyFunction.Flag.WRAPPED);
      }
      if (wrappedFlag == PyFunction.Flag.STATICMETHOD && implicit_offset > 0) {
        implicit_offset -= 1;
      } // might have marked it as implicit 'self'
      if (wrappedFlag == PyFunction.Flag.CLASSMETHOD && !isByInstance) {
        implicit_offset += 1;
      } // Both Foo.method() and foo.method() have implicit the first arg
    }
    if (!isByInstance && PyNames.NEW.equals(method.getName())) implicit_offset += 1; // constructor call
    // decorators?
    if (PyNames.INIT.equals(method.getName())) {
      String refName = callReference instanceof PyReferenceExpression
                       ? ((PyReferenceExpression)callReference).getReferencedName()
                       : null;
      if (!PyNames.INIT.equals(refName)) {   // PY-312
        implicit_offset += 1;
      }
    }
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
            if (flags != null) {
              flags.add(PyFunction.Flag.STATICMETHOD);
            }
            if (isByInstance && implicit_offset > 0) implicit_offset -= 1; // might have marked it as implicit 'self'
          }
          else if (PyNames.CLASSMETHOD.equals(deconame)) {
            if (flags != null) {
              flags.add(PyFunction.Flag.CLASSMETHOD);
            }
            if (!isByInstance) implicit_offset += 1; // Both Foo.method() and foo.method() have implicit the first arg
          }
          // else could be custom decorator processing
        }
      }
    }
    return implicit_offset;
  }

  protected static boolean isByInstance(final PyExpression callee) {
    if (callee instanceof PyReferenceExpression) {
      PyExpression qualifier = ((PyReferenceExpression)callee).getQualifier();
      if (qualifier != null) {
        PyType type = qualifier.getType(TypeEvalContext.fast());
        if ((type instanceof PyClassType) && (!((PyClassType)type).isDefinition())) {
          // we're calling an instance method of qualifier
          return true;
        }
      }
    }
    return false;
  }

}
