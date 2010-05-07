package com.jetbrains.python.psi.impl;

import com.intellij.openapi.util.Pair;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNamedElement;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.resolve.ImplicitResolveResult;
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
  public static PyCallExpression.PyMarkedFunction resolveCallee(PyCallExpression us) {
    PyExpression callee = us.getCallee();
    PyFunction.Flag wrapped_flag = null;
    boolean is_constructor_call = false;
    if (callee instanceof PyReferenceExpression) {
      PyReferenceExpression ref = (PyReferenceExpression)callee;
      QualifiedResolveResult resolveResult = ref.followAssignmentsChain();
      PsiElement resolved = resolveResult.getElement();
      if (resolved instanceof PyClass) {
        resolved = ((PyClass)resolved).findInitOrNew(true); // class to constructor call
        is_constructor_call = true;
      }
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
        PyExpression last_qualifier = resolveResult.getLastQualifier();
        final PyExpression call_reference = us.getCallee();
        boolean is_by_instance = isByInstance(call_reference);
        if (last_qualifier != null) {
          PyType qualifier_type = last_qualifier.getType(TypeEvalContext.fast()); // NOTE: ...or slow()?
          is_by_instance |= (qualifier_type != null && qualifier_type instanceof PyClassType && !((PyClassType)qualifier_type).isDefinition());
        }
        int implicit_offset = getImplicitArgumentCount(call_reference, (PyFunction) resolved, wrapped_flag, flags, is_by_instance);
        if (! is_constructor_call && PyNames.NEW.equals(((PyFunction)resolved).getName())) {
          implicit_offset = Math.min(implicit_offset-1, 0); // case of Class.__new__  
        }
        return new PyCallExpression.PyMarkedFunction((PyFunction)resolved, flags, implicit_offset,
                                                     resolveResult instanceof ImplicitResolveResult);
      }
    }
    return null;
  }

  /**
   * Calls the {@link #getImplicitArgumentCount(PyExpression, PyFunction, PyFunction.Flag, EnumSet<PyFunction.Flag>, boolean) full version}
   * with null flags and with isByInstance inferred directly from call site (won't work with reassigned bound methods).
   * @param callReference the call site, where arguments are given.
   * @param functionBeingCalled resolved method which is being called; plain functions are OK but make little sense.
   * @return a non-negative number of parameters that are implicit to this call.
   */
  public static int getImplicitArgumentCount(final PyExpression callReference, PyFunction functionBeingCalled) {
    return getImplicitArgumentCount(callReference, functionBeingCalled, null, null, isByInstance(callReference));
  }

  /**
   * Finds how many arguments are implicit in a given call.
   * @param callReference the call site, where arguments are given.
   * @param method resolved method which is being called; plain functions are OK but make little sense.
   * @param wrappedFlag value of {@link PyFunction.Flag#WRAPPED} if known.
   * @param flags set of flags to be <i>updated</i> by this call; wrappedFlag's value ends up here, too.
   * @param isByInstance true if the call is known to be by instance (not by class).
   * @return a non-negative number of parameters that are implicit to this call. E.g. for a typical method call 1 is returned
   * because one parameter ('self') is implicit.
   */
  private static int getImplicitArgumentCount(final PyExpression callReference,
                                              PyFunction method,
                                              @Nullable PyFunction.Flag wrappedFlag,
                                              @Nullable EnumSet<PyFunction.Flag> flags,
                                              boolean isByInstance
  ) {
    int implicit_offset = 0;
    if (isByInstance) implicit_offset += 1;
    // wrapped flags?
    if (wrappedFlag != null) {
      if (flags != null) {
        flags.add(wrappedFlag);
        flags.add(PyFunction.Flag.WRAPPED);
      }
      if (wrappedFlag == PyFunction.Flag.STATICMETHOD && implicit_offset > 0) implicit_offset -= 1; // might have marked it as implicit 'self'
      if (wrappedFlag == PyFunction.Flag.CLASSMETHOD && ! isByInstance) implicit_offset += 1; // Both Foo.method() and foo.method() have implicit the first arg
    }
    if (! isByInstance && PyNames.NEW.equals(method.getName())) implicit_offset += 1; // constructor call
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
            if (! isByInstance) implicit_offset += 1; // Both Foo.method() and foo.method() have implicit the first arg
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
