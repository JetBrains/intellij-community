/*
 * Copyright 2000-2013 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jetbrains.python.psi.impl;

import com.intellij.codeInsight.completion.CompletionUtil;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.ResolveResult;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.resolve.QualifiedResolveResult;
import com.jetbrains.python.psi.types.*;
import com.jetbrains.python.toolbox.Maybe;
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
    final List<PyExpression> qualifiers = resolveResult != null ? resolveResult.getQualifiers() : Collections.<PyExpression>emptyList();
    final TypeEvalContext context = resolveContext.getTypeEvalContext();
    if (resolved instanceof PyFunction) {
      final PyFunction function = (PyFunction)resolved;
      final Property property = function.getProperty();
      if (property != null && isQualifiedByInstance(function, qualifiers, context)) {
        final PyType type = function.getReturnType(context, null);
        if (type instanceof PyFunctionType) {
          resolved = ((PyFunctionType)type).getCallable();
        }
        else {
          resolved = null;
        }
      }
    }
    if (resolved instanceof Callable) {
      PyFunction.Modifier modifier = resolved instanceof PyFunction
                                   ? ((PyFunction)resolved).getModifier()
                                   : null;
      if (modifier == null && wrappedModifier != null) {
        modifier = wrappedModifier;
      }
      boolean isByInstance = isConstructorCall || isQualifiedByInstance((Callable)resolved, qualifiers, context)
                             || resolved instanceof PyBoundFunction;
      PyExpression lastQualifier = qualifiers != null && qualifiers.isEmpty() ? null : qualifiers.get(qualifiers.size()-1);
      boolean isByClass = lastQualifier == null ? false : isQualifiedByClass((Callable)resolved, lastQualifier, context);
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
          if ((qualifierClass.isSubclass(resolvedParent) || resolvedParent.isSubclass(qualifierClass))) {
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

  public static PyType getCallType(@NotNull PyCallExpression call, @NotNull TypeEvalContext context) {
    if (!TypeEvalStack.mayEvaluate(call)) {
      return null;
    }
    try {
      PyExpression callee = call.getCallee();
      if (callee instanceof PyReferenceExpression) {
        // hardwired special cases
        if (PyNames.SUPER.equals(callee.getText())) {
          final Maybe<PyType> superCallType = getSuperCallType(call, context);
          if (superCallType.isDefined()) {
            return superCallType.value();
          }
        }
        if ("type".equals(callee.getText())) {
          final PyExpression[] args = call.getArguments();
          if (args.length == 1) {
            final PyExpression arg = args[0];
            final PyType argType = context.getType(arg);
            if (argType instanceof PyClassType) {
              final PyClassType classType = (PyClassType)argType;
              if (!classType.isDefinition()) {
                final PyClass cls = classType.getPyClass();
                return context.getType(cls);
              }
            }
            else {
              return null;
            }
          }
        }
        // normal cases
        final PyResolveContext resolveContext = PyResolveContext.noImplicits().withTypeEvalContext(context);
        ResolveResult[] targets = ((PyReferenceExpression)callee).getReference(resolveContext).multiResolve(false);
        if (targets.length > 0) {
          PsiElement target = targets[0].getElement();
          if (target == null) {
            return null;
          }
          PyClass cls = null;
          PyFunction init = null;
          if (target instanceof PyClass) {
            cls = (PyClass)target;
            init = cls.findInitOrNew(true);
          }
          else if (target instanceof PyFunction) {
            final PyFunction f = (PyFunction)target;
            if (PyNames.INIT.equals(f.getName())) {
              init = f;
              cls = f.getContainingClass();
            }
          }
          if (init != null) {
            final PyType t = init.getReturnType(context, (PyReferenceExpression)callee);
            if (cls != null) {
              if (init.getContainingClass() != cls) {
                if (t instanceof PyCollectionType) {
                  final PyType elementType = ((PyCollectionType)t).getElementType(context);
                  return new PyCollectionTypeImpl(cls, false, elementType);
                }
                return new PyClassTypeImpl(cls, false);
              }
            }
            if (t != null && !(t instanceof PyNoneType)) {
              return t;
            }
            if (cls != null && t == null) {
              final PyFunction newMethod = cls.findMethodByName(PyNames.NEW, true);
              if (newMethod != null && !PyBuiltinCache.getInstance(call).hasInBuiltins(newMethod)) {
                return PyUnionType.createWeakType(new PyClassTypeImpl(cls, false));
              }
            }
          }
          if (cls != null) {
            return new PyClassTypeImpl(cls, false);
          }
          final PyType providedType = PyReferenceExpressionImpl.getReferenceTypeFromProviders(target, context, call);
          if (providedType instanceof PyCallableType) {
            return ((PyCallableType) providedType).getCallType(context, (PyReferenceExpression)callee);
          }
          if (target instanceof Callable) {
            final Callable callable = (Callable)target;
            return callable.getReturnType(context, (PyReferenceExpression)callee);
          }
        }
      }
      if (callee == null) {
        return null;
      }
      else {
        final PyType type = context.getType(callee);
        if (type instanceof PyCallableType) {
          final PyQualifiedExpression callSite = callee instanceof PyQualifiedExpression ? (PyQualifiedExpression)callee : null;
          return ((PyCallableType) type).getCallType(context, callSite);
        }
        return null;
      }
    }
    finally {
      TypeEvalStack.evaluated(call);
    }
  }

  @NotNull
  private static Maybe<PyType> getSuperCallType(@NotNull PyCallExpression call, TypeEvalContext context) {
    final PyExpression callee = call.getCallee();
    if (callee instanceof PyReferenceExpression) {
      PsiElement must_be_super_init = ((PyReferenceExpression)callee).getReference().resolve();
      if (must_be_super_init instanceof PyFunction) {
        PyClass must_be_super = ((PyFunction)must_be_super_init).getContainingClass();
        if (must_be_super == PyBuiltinCache.getInstance(call).getClass(PyNames.SUPER)) {
          PyArgumentList arglist = call.getArgumentList();
          if (arglist != null) {
            final PyClass containingClass = PsiTreeUtil.getParentOfType(call, PyClass.class);
            PyExpression[] args = arglist.getArguments();
            if (args.length > 1) {
              PyExpression first_arg = args[0];
              if (first_arg instanceof PyReferenceExpression) {
                final PyReferenceExpression firstArgRef = (PyReferenceExpression)first_arg;
                final PyExpression qualifier = firstArgRef.getQualifier();
                if (qualifier != null && PyNames.CLASS.equals(firstArgRef.getReferencedName())) {
                  final PsiReference qRef = qualifier.getReference();
                  final PsiElement element = qRef == null ? null : qRef.resolve();
                  if (element instanceof PyParameter) {
                    final PyParameterList parameterList = PsiTreeUtil.getParentOfType(element, PyParameterList.class);
                    if (parameterList != null && element == parameterList.getParameters()[0]) {
                      return new Maybe<PyType>(getSuperCallTypeForArguments(context, containingClass, args[1]));
                    }
                  }
                }
                PsiElement possible_class = firstArgRef.getReference().resolve();
                if (possible_class instanceof PyClass && ((PyClass)possible_class).isNewStyleClass()) {
                  final PyClass first_class = (PyClass)possible_class;
                  return new Maybe<PyType>(getSuperCallTypeForArguments(context, first_class, args[1]));
                }
              }
            }
            else if (((PyFile)call.getContainingFile()).getLanguageLevel().isPy3K() && containingClass != null) {
              return new Maybe<PyType>(getSuperClassUnionType(containingClass));
            }
          }
        }
      }
    }
    return new Maybe<PyType>();
  }

  @Nullable
  private static PyType getSuperCallTypeForArguments(TypeEvalContext context, PyClass firstClass, PyExpression second_arg) {
    // check 2nd argument, too; it should be an instance
    if (second_arg != null) {
      PyType second_type = context.getType(second_arg);
      if (second_type instanceof PyClassType) {
        // imitate isinstance(second_arg, possible_class)
        PyClass secondClass = ((PyClassType)second_type).getPyClass();
        if (CompletionUtil.getOriginalOrSelf(firstClass) == secondClass) {
          return getSuperClassUnionType(firstClass);
        }
        if (secondClass.isSubclass(firstClass)) {
          final Iterator<PyClass> iterator = firstClass.getAncestorClasses(context).iterator();
          if (iterator.hasNext()) {
            return new PyClassTypeImpl(iterator.next(), false); // super(Foo, self) has type of Foo, modulo __get__()
          }
        }
      }
    }
    return null;
  }

  @Nullable
  private static PyType getSuperClassUnionType(@NotNull PyClass pyClass) {
    // TODO: this is closer to being correct than simply taking first superclass type but still not entirely correct;
    // super can also delegate to sibling types
    // TODO handle __mro__ here
    final PyClass[] supers = pyClass.getSuperClasses();
    if (supers.length > 0) {
      if (supers.length == 1) {
        return new PyClassTypeImpl(supers[0], false);
      }
      List<PyType> superTypes = new ArrayList<PyType>();
      for (PyClass aSuper : supers) {
        superTypes.add(new PyClassTypeImpl(aSuper, false));
      }
      return PyUnionType.union(superTypes);
    }
    return null;
  }
}
