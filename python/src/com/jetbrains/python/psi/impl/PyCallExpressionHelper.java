package com.jetbrains.python.psi.impl;

import com.intellij.openapi.util.Pair;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.resolve.QualifiedResolveResult;
import com.jetbrains.python.psi.types.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * Functions common to different implementors of PyCallExpression, with different base classes.
 * User: dcheryasov
 * Date: Dec 23, 2008 10:31:38 AM
 */
public class PyCallExpressionHelper {
  private PyCallExpressionHelper() {
    // none
  }

  /**
   * Adds an argument to the end of argument list.
   * @param us the arg list
   * @param expression what to add
   */
  public static void addArgument(PyCallExpression us, PyExpression expression) {
    PyExpression[] arguments = us.getArgumentList().getArguments();
    final PyExpression last_arg = arguments.length == 0 ? null : arguments[arguments.length - 1];
    PyElementGenerator.getInstance(us.getProject()).insertItemIntoList(us, last_arg, expression);
  }

  /**
   * Tries to interpret a call as a call to built-in {@code classmethod} or {@code staticmethod}.
   *
   * @param redefiningCall the possible call, generally a result of chasing a chain of assignments
   * @param us              any in-project PSI element, used to determine SDK and ultimately builtins module used to check the wrapping functions
   * @return a pair of wrapper name and wrapped function; for {@code staticmethod(foo)} it would be ("staticmethod", foo).
   */
  @Nullable
  public static Pair<String, PyFunction> interpretAsModifierWrappingCall(PyCallExpression redefiningCall, PsiElement us) {
    PyExpression redefining_callee = redefiningCall.getCallee();
    if (redefiningCall.isCalleeText(PyNames.CLASSMETHOD, PyNames.STATICMETHOD)) {
      final PyReferenceExpression refex = (PyReferenceExpression)redefining_callee;
      final String refname = refex.getReferencedName();
      if ((PyNames.CLASSMETHOD.equals(refname) || PyNames.STATICMETHOD.equals(refname))) {
        PsiElement redefining_func = refex.getReference().resolve();
        if (redefining_func != null) {
          PsiElement true_func = PyBuiltinCache.getInstance(us).getByName(refname);
          if (true_func instanceof PyClass) true_func = ((PyClass)true_func).findInitOrNew(true);
          if (true_func == redefining_func) {
            // yes, really a case of "foo = classmethod(foo)"
            PyArgumentList arglist = redefiningCall.getArgumentList();
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
      resolveResult = ref.followAssignmentsChain(PyResolveContext.noImplicits());
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
  public static Callable resolveCalleeFunction(PyCallExpression call, PyResolveContext resolveContext) {
    PsiElement resolved;
    PyExpression callee = call.getCallee();
    if (callee instanceof PyReferenceExpression) {
      // dereference
      PyReferenceExpression ref = (PyReferenceExpression)callee;
      QualifiedResolveResult resolveResult = ref.followAssignmentsChain(resolveContext);
      resolved = resolveResult.getElement();
    }
    else {
      resolved = callee;
    }
    if (resolved instanceof PyClass) {
      resolved = ((PyClass)resolved).findInitOrNew(true); // class to constructor call
    }
    else if (resolved instanceof PyCallExpression) {
      PyCallExpression redefiningCall = (PyCallExpression)resolved;
      Pair<String, PyFunction> wrapperInfo = interpretAsModifierWrappingCall(redefiningCall, call);
      if (wrapperInfo != null) {
        resolved = wrapperInfo.getSecond();
      }
    }
    if (resolved instanceof Callable) {
      return (Callable) resolved;
    }
    return null;
  }
  

  @Nullable
  public static PyCallExpression.PyMarkedCallee resolveCallee(PyCallExpression us, PyResolveContext resolveContext) {
    return resolveCallee(us, resolveContext, 0);
  }

  @Nullable
  public static PyCallExpression.PyMarkedCallee resolveCallee(PyCallExpression us, PyResolveContext resolveContext, int implicitOffset) {
    PyFunction.Modifier wrappedModifier = null;
    boolean isConstructorCall = false;

    PyExpression callee = us.getCallee();
    PsiElement resolved;
    QualifiedResolveResult resolveResult = null;
    if (callee instanceof PyReferenceExpression) {
      // dereference
      PyReferenceExpression ref = (PyReferenceExpression)callee;
      resolveResult = ref.followAssignmentsChain(resolveContext);
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
      Pair<String, PyFunction> wrapperInfo = interpretAsModifierWrappingCall(redefiningCall, us);
      if (wrapperInfo != null) {
        resolved = wrapperInfo.getSecond();
        String wrapper_name = wrapperInfo.getFirst();
        if (PyNames.CLASSMETHOD.equals(wrapper_name)) {
          wrappedModifier = PyFunction.Modifier.CLASSMETHOD;
        }
        else if (PyNames.STATICMETHOD.equals(wrapper_name)) wrappedModifier = PyFunction.Modifier.STATICMETHOD;
      }
    }
    if (resolved instanceof Callable) {
      PyFunction.Modifier modifier = resolved instanceof PyFunction
                                   ? ((PyFunction)resolved).getModifier()
                                   : null;
      if (modifier == null && wrappedModifier != null) {
        modifier = wrappedModifier;
      }
      List<PyExpression> qualifiers = resolveResult != null ? resolveResult.getQualifiers() : Collections.<PyExpression>emptyList();
      boolean isByInstance = isConstructorCall ||
                               isQualifiedByInstance((Callable)resolved, qualifiers, resolveContext.getTypeEvalContext())
                               || resolved instanceof PyBoundFunction;
      PyExpression lastQualifier = qualifiers != null && qualifiers.isEmpty() ? null : qualifiers.get(qualifiers.size()-1);
      boolean isByClass = lastQualifier == null ? false : isQualifiedByClass((Callable)resolved, lastQualifier, resolveContext.getTypeEvalContext());
      final Callable callable = (Callable)resolved;

      implicitOffset += getImplicitArgumentCount(callable, modifier, isConstructorCall, isByInstance, isByClass);
      implicitOffset = implicitOffset < 0? 0: implicitOffset; // wrong source can trigger strange behaviour
      return new PyCallExpression.PyMarkedCallee(callable, modifier, implicitOffset,
                                                 resolveResult != null ? resolveResult.isImplicit() : false);
    }
    return null;
  }

  /**
   * Calls the {@link #getImplicitArgumentCount(PyExpression, Callable, com.jetbrains.python.psi.PyFunction.Modifier, EnumSet< com.jetbrains.python.psi.PyFunction.Modifier >, boolean) full version}
   * with null flags and with isByInstance inferred directly from call site (won't work with reassigned bound methods).
   *
   * @param callReference       the call site, where arguments are given.
   * @param functionBeingCalled resolved method which is being called; plain functions are OK but make little sense.
   * @return a non-negative number of parameters that are implicit to this call.
   */
  public static int getImplicitArgumentCount(
    @NotNull final PyReferenceExpression callReference,
    @NotNull PyFunction functionBeingCalled) {
    //return getImplicitArgumentCount(functionBeingCalled, null, null, qualifierIsAnInstance(callReference, TypeEvalContext.fast()));
    final PyDecorator decorator = PsiTreeUtil.getParentOfType(callReference, PyDecorator.class);
    if (decorator != null && PsiTreeUtil.isAncestor(decorator.getCallee(), callReference, false)) {
      return 1;
    }
    final PyResolveContext resolveContext = PyResolveContext.noImplicits();
    QualifiedResolveResult followed = callReference.followAssignmentsChain(resolveContext);
    boolean isByInstance = isQualifiedByInstance(functionBeingCalled, followed.getQualifiers(), resolveContext.getTypeEvalContext());
    boolean isByClass = isQualifiedByInstance(functionBeingCalled, followed.getQualifiers(), resolveContext.getTypeEvalContext());
    return getImplicitArgumentCount(functionBeingCalled, functionBeingCalled.getModifier(), false, isByInstance, isByClass);
  }

  /**
   * Finds how many arguments are implicit in a given call.
   *
   * @param callable      resolved method which is being called; non-methods immediately return 0.
   * @param flags         set of flags for the call
   * @param isByInstance  true if the call is known to be by instance (not by class).
   * @return a non-negative number of parameters that are implicit to this call. E.g. for a typical method call 1 is returned
   *         because one parameter ('self') is implicit.
   */
  private static int getImplicitArgumentCount(
    Callable callable,
    PyFunction.Modifier modifier,
    boolean isConstructorCall,
    boolean isByInstance,
    boolean isByClass
  ) {
    int implicit_offset = 0;
    boolean firstIsArgsOrKwargs = false;
    final PyParameter[] parameters = callable.getParameterList().getParameters();
    if (parameters.length > 0) {
      final PyParameter first = parameters[0];
      final PyNamedParameter named = first.getAsNamed();
      if (named != null && (named.isPositionalContainer() || named.isKeywordContainer())) {
        firstIsArgsOrKwargs = true;
      }
    }
    if (!firstIsArgsOrKwargs && (isByInstance || isConstructorCall)) {
      implicit_offset += 1;
    }
    PyFunction method = callable.asMethod();
    if (method == null) return implicit_offset;

    if (PyNames.NEW.equals(method.getName())) {
      return isConstructorCall ? 1 : 0;
    }
    if (!isByInstance && !isByClass && PyNames.INIT.equals(method.getName())) {
      return 1;
    }

    // decorators?
    if (modifier == PyFunction.Modifier.STATICMETHOD) {
      if (isByInstance && implicit_offset > 0) implicit_offset -= 1; // might have marked it as implicit 'self'
    }
    else if (modifier == PyFunction.Modifier.CLASSMETHOD) {
      if (!isByInstance) implicit_offset += 1; // Both Foo.method() and foo.method() have implicit the first arg
    }
    return implicit_offset;
  }

  private static boolean isQualifiedByInstance(Callable resolved, List<PyExpression> qualifiers, TypeEvalContext context) {
    PyDocStringOwner owner = PsiTreeUtil.getStubOrPsiParentOfType(resolved, PyDocStringOwner.class);
    if (!(owner instanceof PyClass)) {
      return false;
    }
    // true = call by instance
    if (qualifiers.isEmpty()) {
      return true; // unqualified + method = implicit constructor call
    }
    for (PyExpression qualifier : qualifiers) {
      if (isQualifiedByInstance(resolved, qualifier, context)) {
        return true;
      }
    }
    return false;
  }

  private static boolean isQualifiedByInstance(Callable resolved, PyExpression qualifier, TypeEvalContext context) {
    if (isQualifiedByClass(resolved, qualifier, context)) {
      return false;
    }
    PyType qtype = context.getType(qualifier);
    if (qtype != null) {
      // TODO: handle UnionType
      if (qtype instanceof PyModuleType) return false; // qualified by module, not instance.
    }
    return true; // NOTE. best guess: unknown qualifier is more probably an instance.
  }

  private static boolean isQualifiedByClass(Callable resolved, PyExpression qualifier, TypeEvalContext context) {
    PyType qtype = context.getType(qualifier);
    if (qtype instanceof PyClassType) {
      if (((PyClassType)qtype).isDefinition()) {
        PyClass resolvedParent = PsiTreeUtil.getStubOrPsiParentOfType(resolved, PyClass.class);
        if (resolvedParent != null) {
          final PyClass qualifierClass = ((PyClassType)qtype).getPyClass();
          if (qualifierClass != null && (qualifierClass.isSubclass(resolvedParent) || resolvedParent.isSubclass(qualifierClass))) {
            return true;
          }
        }
      }
    }
    return false;
  }

  static boolean isCalleeText(PyCallExpression pyCallExpression, String[] nameCandidates) {
    final PyExpression callee = pyCallExpression.getCallee();
    if (!(callee instanceof PyReferenceExpression)) {
      return false;
    }
    for (String name : nameCandidates) {
      if (name.equals(((PyReferenceExpression)callee).getReferencedName())) {
        return true;
      }
    }
    return false;
  }

  @Nullable
  public static PyExpression getKeywordArgument(PyCallExpression expr, String keyword) {
    for (PyExpression arg : expr.getArguments()) {
      if (arg instanceof PyKeywordArgument) {
        PyKeywordArgument kwarg = (PyKeywordArgument)arg;
        if (keyword.equals(kwarg.getKeyword())) {
          return kwarg.getValueExpression();
        }
      }
    }
    return null;
  }
}
