// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.psi.impl;

import com.intellij.codeInsight.completion.CompletionUtilCoreImpl;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import com.intellij.psi.ResolveResult;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.ThreeState;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.PythonRuntimeService;
import com.jetbrains.python.ast.PyAstFunction;
import com.jetbrains.python.codeInsight.controlflow.ScopeOwner;
import com.jetbrains.python.codeInsight.dataflow.scope.ScopeUtil;
import com.jetbrains.python.codeInsight.typing.PyTypingTypeProvider;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.references.PyReferenceImpl;
import com.jetbrains.python.psi.resolve.*;
import com.jetbrains.python.psi.types.*;
import com.jetbrains.python.pyi.PyiUtil;
import com.jetbrains.python.toolbox.Maybe;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Functions common to different implementors of PyCallExpression, with different base classes.
 */
public final class PyCallExpressionHelper {
  private PyCallExpressionHelper() {
  }

  /**
   * Tries to interpret a call as a call to built-in {@code classmethod} or {@code staticmethod}.
   *
   * @param redefiningCall the possible call, generally a result of chasing a chain of assignments
   * @return a pair of wrapper name and wrapped function; for {@code staticmethod(foo)} it would be ("staticmethod", foo).
   */
  @Nullable
  public static Pair<String, PyFunction> interpretAsModifierWrappingCall(PyCallExpression redefiningCall) {
    PyExpression redefining_callee = redefiningCall.getCallee();
    if (redefiningCall.isCalleeText(PyNames.CLASSMETHOD, PyNames.STATICMETHOD)) {
      final PyReferenceExpression referenceExpr = (PyReferenceExpression)redefining_callee;
      if (referenceExpr != null) {
        final String refName = referenceExpr.getReferencedName();
        if ((PyNames.CLASSMETHOD.equals(refName) || PyNames.STATICMETHOD.equals(refName)) && PyBuiltinCache.isInBuiltins(referenceExpr)) {
          // yes, really a case of "foo = classmethod(foo)"
          PyArgumentList argumentList = redefiningCall.getArgumentList();
          if (argumentList != null) { // really can't be any other way
            PyExpression[] args = argumentList.getArguments();
            if (args.length == 1) {
              PyExpression possible_original_ref = args[0];
              if (possible_original_ref instanceof PyReferenceExpression) {
                PsiElement original = ((PyReferenceExpression)possible_original_ref).getReference().resolve();
                if (original instanceof PyFunction) {
                  // pinned down the original; replace our resolved callee with it and add flags.
                  return Pair.create(refName, (PyFunction)original);
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
    QualifiedResolveResult resolveResult;
    if (callee instanceof PyReferenceExpression ref) {
      // dereference
      resolveResult = ref.followAssignmentsChain(PyResolveContext.defaultContext(TypeEvalContext.codeInsightFallback(ref.getProject())));
      resolved = resolveResult.getElement();
    }
    else {
      resolved = callee;
    }
    // analyze
    if (resolved instanceof PyClass) {
      return (PyClass)resolved;
    }
    else if (resolved instanceof PyFunction pyFunction) {
      return pyFunction.getContainingClass();
    }

    return null;
  }

  /**
   * This method should not be called directly,
   * please obtain its result via {@link TypeEvalContext#getType} with {@code call.getCallee()} as an argument.
   */
  static @Nullable PyType getCalleeType(@NotNull PyCallExpression call,
                                        @NotNull PyResolveContext resolveContext) {
    final List<PyType> callableTypes = new ArrayList<>();
    final TypeEvalContext context = resolveContext.getTypeEvalContext();

    final List<QualifiedRatedResolveResult> results =
      PyUtil.filterTopPriorityResults(
        forEveryScopeTakeOverloadsOtherwiseImplementations(
          multiResolveCallee(call.getCallee(), resolveContext),
          RatedResolveResult::getElement,
          context
        )
      );

    for (QualifiedRatedResolveResult resolveResult : results) {
      final PsiElement element = resolveResult.getElement();
      if (element != null) {
        final PyType typeFromProviders =
          Ref.deref(PyReferenceExpressionImpl.getReferenceTypeFromProviders(element, resolveContext.getTypeEvalContext(), call));

        if (PyTypeUtil.toStream(typeFromProviders).allMatch(it -> it instanceof PyCallableType)) {
          PyTypeUtil.toStream(typeFromProviders).forEachOrdered(callableTypes::add);
          continue;
        }
      }

      for (ClarifiedResolveResult clarifiedResolveResult : clarifyResolveResult(resolveResult, resolveContext)) {
        ContainerUtil.addIfNotNull(callableTypes, toCallableType(call, clarifiedResolveResult, context));
      }
    }

    return PyUnionType.union(callableTypes);
  }

  /**
   * It is not the same as {@link PyCallExpressionHelper#getCalleeType} since
   * this method returns callable types that would be actually called, the mentioned method returns type of underlying callee.
   * Compare:
   * <pre>
   * {@code
   * class A:
   *   pass
   * a = A()
   * b = a()  # callee type is A, resolved callee is A.__call__
   * }
   * </pre>
   */
  @NotNull
  static List<@NotNull PyCallableType> multiResolveCallee(@NotNull PyCallExpression call, @NotNull PyResolveContext resolveContext) {
    return PyUtil.getParameterizedCachedValue(
      call,
      resolveContext,
      it -> ContainerUtil.concat(
          getExplicitResolveResults(call, it),
          getImplicitResolveResults(call, it),
          getRemoteResolveResults(call, it))
    );
  }

  private static @NotNull List<@NotNull PyCallableType> multiResolveCallee(@NotNull PyReferenceOwner subscription,
                                                                           @NotNull PyResolveContext resolveContext) {
    final TypeEvalContext context = resolveContext.getTypeEvalContext();

    final var results = forEveryScopeTakeOverloadsOtherwiseImplementations(
      // Remove the artificial latest overloads from results so as not to spoil their original order
      ContainerUtil.filter(subscription.getReference(resolveContext).multiResolve(false), result ->
        !(result instanceof RatedResolveResult rrr) || rrr.getRate() != RatedResolveResult.RATE_LIFTED_PY_FILE_OVERLOAD
      ),
      context
    );

    return selectCallableTypes(results, context);
  }

  @NotNull
  private static List<@NotNull PyCallableType> getExplicitResolveResults(@NotNull PyCallExpression call,
                                                                         @NotNull PyResolveContext resolveContext) {
    final var callee = call.getCallee();
    if (callee == null) return Collections.emptyList();

    final TypeEvalContext context = resolveContext.getTypeEvalContext();
    final var calleeType = context.getType(callee);

    final var provided = StreamEx
      .of(PyTypeProvider.EP_NAME.getExtensionList())
      .map(e -> e.prepareCalleeTypeForCall(calleeType, call, context))
      .nonNull()
      .toList();
    if (!provided.isEmpty()) return ContainerUtil.mapNotNull(provided, Ref::deref);

    final List<PyCallableType> result = new ArrayList<>();

    for (PyType type : PyTypeUtil.toStream(calleeType)) {
      if (type instanceof PyClassType classType) {

        final var implicitlyInvokedMethods =
          forEveryScopeTakeOverloadsOtherwiseImplementations(
            resolveImplicitlyInvokedMethods(classType, call, resolveContext),
            context
          );

        if (implicitlyInvokedMethods.isEmpty()) {
          result.add(classType);
        }
        else {
          result.addAll(changeToImplicitlyInvokedMethods(classType, implicitlyInvokedMethods, call, context));
        }
      }
      else if (type instanceof PyCallableType) {
        result.add((PyCallableType)type);
      }
    }

    return result;
  }

  @NotNull
  private static List<@NotNull PyCallableType> getImplicitResolveResults(@NotNull PyCallExpression call,
                                                                         @NotNull PyResolveContext resolveContext) {
    if (!resolveContext.allowImplicits()) return Collections.emptyList();

    final PyExpression callee = call.getCallee();
    final TypeEvalContext context = resolveContext.getTypeEvalContext();
    if (callee instanceof PyQualifiedExpression qualifiedCallee) {
      final String referencedName = qualifiedCallee.getReferencedName();
      if (referencedName == null) return Collections.emptyList();

      final PyExpression qualifier = qualifiedCallee.getQualifier();
      if (qualifier == null || !canQualifyAnImplicitName(qualifier)) return Collections.emptyList();

      final PyType qualifierType = context.getType(qualifier);
      if (PyTypeChecker.isUnknown(qualifierType, context) ||
          qualifierType instanceof PyStructuralType && ((PyStructuralType)qualifierType).isInferredFromUsages()) {
        final ResolveResultList resolveResults = new ResolveResultList();
        PyResolveUtil.addImplicitResolveResults(referencedName, resolveResults, qualifiedCallee);

        return selectCallableTypes(forEveryScopeTakeOverloadsOtherwiseImplementations(resolveResults, context), context);
      }
    }

    return Collections.emptyList();
  }

  @NotNull
  private static List<@NotNull PyCallableType> getRemoteResolveResults(@NotNull PyCallExpression call,
                                                                       @NotNull PyResolveContext resolveContext) {
    if (!resolveContext.allowRemote()) return Collections.emptyList();
    PsiFile file = call.getContainingFile();
    if (file == null || !PythonRuntimeService.getInstance().isInPydevConsole(file)) return Collections.emptyList();
    PyType calleeType = getCalleeType(call, resolveContext);
    return PyTypeUtil.toStream(calleeType).select(PyCallableType.class).toList();
  }

  @NotNull
  private static List<@NotNull PyCallableType> selectCallableTypes(@NotNull List<PsiElement> resolveResults,
                                                                   @NotNull TypeEvalContext context) {
    return StreamEx
      .of(resolveResults)
      .select(PyTypedElement.class)
      .map(context::getType)
      .flatMap(PyTypeUtil::toStream)
      .select(PyCallableType.class)
      .toList();
  }

  @NotNull
  private static List<QualifiedRatedResolveResult> multiResolveCallee(@Nullable PyExpression callee,
                                                                      @NotNull PyResolveContext resolveContext) {
    if (callee instanceof PyReferenceExpression) {
      return ((PyReferenceExpression)callee).multiFollowAssignmentsChain(resolveContext);
    }
    else if (callee instanceof PyLambdaExpression) {
      return Collections.singletonList(
        new QualifiedRatedResolveResult(callee, Collections.emptyList(), RatedResolveResult.RATE_NORMAL, false)
      );
    }

    return Collections.emptyList();
  }

  @NotNull
  private static List<ClarifiedResolveResult> clarifyResolveResult(@NotNull QualifiedRatedResolveResult resolveResult,
                                                                   @NotNull PyResolveContext resolveContext) {
    final PsiElement resolved = resolveResult.getElement();

    if (resolved instanceof PyCallExpression resolvedCall) { // foo = classmethod(foo)

      final Pair<String, PyFunction> wrapperInfo = interpretAsModifierWrappingCall(resolvedCall);
      if (wrapperInfo != null) {
        final String wrapperName = wrapperInfo.getFirst();
        final PyFunction.Modifier wrappedModifier = PyNames.CLASSMETHOD.equals(wrapperName)
                                                    ? PyAstFunction.Modifier.CLASSMETHOD
                                                    : PyNames.STATICMETHOD.equals(wrapperName)
                                                      ? PyAstFunction.Modifier.STATICMETHOD
                                                      : null;

        final ClarifiedResolveResult result = new ClarifiedResolveResult(resolveResult, wrapperInfo.getSecond(), wrappedModifier, false);
        return Collections.singletonList(result);
      }
    }
    else if (resolved instanceof PyFunction function) {
      final TypeEvalContext context = resolveContext.getTypeEvalContext();

      if (function.getProperty() != null && isQualifiedByInstance(function, resolveResult.getQualifiers(), context)) {
        final PyType type = context.getReturnType(function);

        return type instanceof PyFunctionType
               ? Collections.singletonList(new ClarifiedResolveResult(resolveResult, ((PyFunctionType)type).getCallable(), null, false))
               : Collections.emptyList();
      }
    }

    return resolved != null
           ? Collections.singletonList(new ClarifiedResolveResult(resolveResult, resolved, null, resolved instanceof PyClass))
           : Collections.emptyList();
  }

  private static @Nullable PyCallableType toCallableType(@NotNull PyCallSiteExpression callSite,
                                                         @NotNull ClarifiedResolveResult resolveResult,
                                                         @NotNull TypeEvalContext context) {
    final PsiElement clarifiedResolved = resolveResult.myClarifiedResolved;
    if (!(clarifiedResolved instanceof PyTypedElement)) return null;

    final PyCallableType callableType = PyUtil.as(context.getType((PyTypedElement)clarifiedResolved), PyCallableType.class);
    if (callableType == null) return null;

    if (clarifiedResolved instanceof PyCallable callable) {

      final PyFunction.Modifier originalModifier = callable instanceof PyFunction ? ((PyFunction)callable).getModifier() : null;
      final PyFunction.Modifier resolvedModifier = ObjectUtils.chooseNotNull(originalModifier, resolveResult.myWrappedModifier);

      final boolean isConstructorCall = resolveResult.myIsConstructor;
      final List<PyExpression> qualifiers = resolveResult.myOriginalResolveResult.getQualifiers();

      final boolean isByInstance = isConstructorCall
                                   || isQualifiedByInstance(callable, qualifiers, context);

      final PyExpression lastQualifier = ContainerUtil.getLastItem(qualifiers);
      final boolean isByClass = lastQualifier != null && isQualifiedByClass(callable, lastQualifier, context);

      final int resolvedImplicitOffset =
        getImplicitArgumentCount(callable, resolvedModifier, isConstructorCall, isByInstance, isByClass);

      final PyType clarifiedConstructorCallType =
        PyUtil.isInitOrNewMethod(clarifiedResolved) ? clarifyConstructorCallType(resolveResult, callSite, context) : null;

      if (callableType.getModifier() == resolvedModifier &&
          callableType.getImplicitOffset() == resolvedImplicitOffset &&
          clarifiedConstructorCallType == null) {
        return callableType;
      }

      return new PyCallableTypeImpl(
        callableType.getParameters(context),
        ObjectUtils.chooseNotNull(clarifiedConstructorCallType, callableType.getCallType(context, callSite)),
        callable,
        resolvedModifier,
        Math.max(0, resolvedImplicitOffset)); // wrong source can trigger strange behaviour
    }

    return callableType;
  }

  /**
   * Calls the {@link #getImplicitArgumentCount(PyCallable, PyFunction.Modifier, boolean, boolean, boolean)} (full version)
   * with null flags and with isByInstance inferred directly from call site (won't work with reassigned bound methods).
   *
   * @param callReference the call site, where arguments are given.
   * @param function      resolved method which is being called; plain functions are OK but make little sense.
   * @return a non-negative number of parameters that are implicit to this call.
   */
  public static int getImplicitArgumentCount(@NotNull final PyReferenceExpression callReference, @NotNull PyFunction function,
                                             @NotNull PyResolveContext resolveContext) {
    QualifiedResolveResult followed = callReference.followAssignmentsChain(resolveContext);
    final List<PyExpression> qualifiers = followed.getQualifiers();
    final PyExpression firstQualifier = ContainerUtil.getFirstItem(qualifiers);
    boolean isByInstance = isQualifiedByInstance(function, qualifiers, resolveContext.getTypeEvalContext());
    String name = callReference.getName();
    final boolean isConstructorCall = PyUtil.isInitOrNewMethod(function) &&
                                      (!callReference.isQualified() || !PyNames.INIT.equals(name) && !PyNames.NEW.equals(name));
    boolean isByClass = firstQualifier != null && isQualifiedByClass(function, firstQualifier, resolveContext.getTypeEvalContext());
    return getImplicitArgumentCount(function, function.getModifier(), isConstructorCall, isByInstance, isByClass);
  }

  /**
   * Finds how many arguments are implicit in a given call.
   *
   * @param callable     resolved method which is being called; non-methods immediately return 0.
   * @param isByInstance true if the call is known to be by instance (not by class).
   * @return a non-negative number of parameters that are implicit to this call. E.g. for a typical method call 1 is returned
   * because one parameter ('self') is implicit.
   */
  private static int getImplicitArgumentCount(
    PyCallable callable,
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

    if (PyUtil.isNewMethod(method)) {
      return isConstructorCall ? 1 : 0;
    }
    if (!isByInstance && !isByClass && PyUtil.isInitMethod(method)) {
      return 1;
    }

    // decorators?
    if (modifier == PyAstFunction.Modifier.STATICMETHOD) {
      if (isByInstance && implicit_offset > 0) implicit_offset -= 1; // might have marked it as implicit 'self'
    }
    else if (modifier == PyAstFunction.Modifier.CLASSMETHOD) {
      if (!isByInstance) implicit_offset += 1; // Both Foo.method() and foo.method() have implicit the first arg
    }
    return implicit_offset;
  }

  private static boolean isQualifiedByInstance(@Nullable PyCallable resolved, @NotNull List<PyExpression> qualifiers,
                                               @NotNull TypeEvalContext context) {
    PyDocStringOwner owner = PsiTreeUtil.getStubOrPsiParentOfType(resolved, PyDocStringOwner.class);
    if (!(owner instanceof PyClass)) {
      return false;
    }
    // true = call by instance
    if (qualifiers.isEmpty()) {
      return true; // unqualified + method = implicit constructor call
    }
    for (PyExpression qualifier : qualifiers) {
      if (qualifier != null) {
        ThreeState byInstance = isQualifiedByInstance(resolved, qualifier, context);
        if (byInstance != ThreeState.UNSURE) {
          return byInstance.toBoolean();
        }
      }
    }
    return true;
  }

  private static @NotNull ThreeState isQualifiedByInstance(@Nullable PyCallable resolved,
                                                           @NotNull PyExpression qualifier,
                                                           @NotNull TypeEvalContext context) {
    if (isQualifiedByClass(resolved, qualifier, context)) {
      return ThreeState.NO;
    }
    final PyType qualifierType = context.getType(qualifier);
    // TODO: handle UnionType
    if (qualifierType instanceof PyModuleType) {
      return ThreeState.UNSURE;
    }
    return ThreeState.YES; // NOTE. best guess: unknown qualifier is more probably an instance.
  }

  private static boolean isQualifiedByClass(@Nullable PyCallable resolved,
                                            @NotNull PyExpression qualifier,
                                            @NotNull TypeEvalContext context) {
    final PyType qualifierType = context.getType(qualifier);

    if (qualifierType instanceof PyClassType qualifierClassType) {
      return qualifierClassType.isDefinition() && belongsToSpecifiedClassHierarchy(resolved, qualifierClassType.getPyClass(), context);
    }
    else if (qualifierType instanceof PyClassLikeType) {
      return ((PyClassLikeType)qualifierType).isDefinition(); // Any definition means callable is classmethod
    }
    else if (qualifierType instanceof PyUnionType) {
      final Collection<PyType> members = ((PyUnionType)qualifierType).getMembers();

      if (ContainerUtil.all(members, type -> type == null || type instanceof PyNoneType || type instanceof PyClassType)) {
        return StreamEx
          .of(members)
          .select(PyClassType.class)
          .filter(type -> belongsToSpecifiedClassHierarchy(resolved, type.getPyClass(), context))
          .allMatch(PyClassType::isDefinition);
      }
    }

    return false;
  }

  private static boolean belongsToSpecifiedClassHierarchy(@Nullable PsiElement element,
                                                          @NotNull PyClass cls,
                                                          @NotNull TypeEvalContext context) {
    final PyClass parent = PsiTreeUtil.getStubOrPsiParentOfType(element, PyClass.class);
    return parent != null && (cls.isSubclass(parent, context) || parent.isSubclass(cls, context));
  }

  /**
   * This method should not be called directly,
   * please obtain its result via {@link TypeEvalContext#getType} with {@code call} as an argument.
   */
  static @Nullable PyType getCallType(@NotNull PyCallExpression call,
                                      @NotNull TypeEvalContext context,
                                      @SuppressWarnings("unused") @NotNull TypeEvalContext.Key key) {
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
          if (argType instanceof PyClassType classType) {
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
    }
    if (callee instanceof PySubscriptionExpression) {
      final PyType parametrizedType = Ref.deref(PyTypingTypeProvider.getType(callee, context));
      if (parametrizedType != null) {
        return parametrizedType;
      }
    }
    final PyResolveContext resolveContext = PyResolveContext.defaultContext(context);
    return getCallType(multiResolveCallee(call, resolveContext), call, context);
  }

  /**
   * This method should not be called directly,
   * please obtain its result via {@link TypeEvalContext#getType} with {@code subscription} as an argument.
   */
  static @Nullable PyType getCallType(@NotNull PySubscriptionExpression subscription,
                                      @NotNull TypeEvalContext context,
                                      @SuppressWarnings("unused") @NotNull TypeEvalContext.Key key) {
    final PyResolveContext resolveContext = PyResolveContext.defaultContext(context);
    return getCallType(multiResolveCallee((PyReferenceOwner)subscription, resolveContext), subscription, context);
  }

  private static @Nullable PyType getCallType(@NotNull List<@NotNull PyCallableType> callableTypes,
                                              @NotNull PyCallSiteExpression callSite,
                                              @NotNull TypeEvalContext context) {
    Map<Pair<ScopeOwner, Boolean>, List<PyCallableType>> callableByScopeBins = StreamEx.of(callableTypes)
      .filter(PyCallableType::isCallable)
      .groupingBy(type -> {
        PyCallable callable = type.getCallable();
        return Pair.create(ScopeUtil.getScopeOwner(callable), callable != null && PyiUtil.isOverload(callable, context));
      }, LinkedHashMap::new, Collectors.toList());

    return StreamEx.of(callableByScopeBins.values())
      .flatCollection(callables -> getSameScopeCallablesCallTypes(callables, callSite, context))
      .collect(PyTypeUtil.toUnion());
  }

  public static @Nullable PyType getCallType(@NotNull PyBinaryExpression binaryExpression,
                                             @NotNull TypeEvalContext context,
                                             @NotNull TypeEvalContext.Key key) {
    final PyResolveContext resolveContext = PyResolveContext.defaultContext(context);
    List<@NotNull PyCallableType> callableTypes = multiResolveCallee((PyReferenceOwner)binaryExpression, resolveContext);
    // TODO split normal and reflected operator methods and process them separately
    //  e.g. if there is matching __add__ of the left operand, don't consider signatures of __radd__ of the right operand, etc.  
    List<PyCallableType> matchingCallableTypes = ContainerUtil.filter(
      callableTypes, 
      callable -> callable.getCallable() instanceof PyFunction function && matchesByArgumentTypes(function, binaryExpression, context)
    );
    return matchingCallableTypes.isEmpty() ? getCallType(callableTypes, binaryExpression, context) 
                                           : getCallType(matchingCallableTypes, binaryExpression, context);
  }

  private static @NotNull List<PyType> getSameScopeCallablesCallTypes(@NotNull List<PyCallableType> callables,
                                                                      @NotNull PyCallSiteExpression callSite,
                                                                      @NotNull TypeEvalContext context) {
    @Nullable PyCallable firstCallable = callables.get(0).getCallable();
    if (firstCallable != null && PyiUtil.isOverload(firstCallable, context)) {
      return Collections.singletonList(resolveOverloadsCallType(callables, callSite, context));
    }
    return ContainerUtil.map(callables, callable -> callable.getCallType(context, callSite));
  }

  private static @Nullable PyType resolveOverloadsCallType(@NotNull List<PyCallableType> overloads,
                                                           @NotNull PyCallSiteExpression callSite,
                                                           @NotNull TypeEvalContext context) {
    List<PyExpression> arguments = callSite.getArguments(overloads.get(0).getCallable());
    List<PyCallableType> matchingOverloads = ContainerUtil.filter(
      overloads,
      overload -> matchesByArgumentTypes((PyFunction)overload.getCallable(), callSite, context)
    );
    if (matchingOverloads.isEmpty()) {
      return StreamEx.of(overloads)
        .map(overload -> overload.getCallType(context, callSite))
        .collect(PyTypeUtil.toUnion());
    }
    if (matchingOverloads.size() == 1) {
      return matchingOverloads.get(0).getCallType(context, callSite);
    }
    boolean someArgumentsHaveUnknownType = ContainerUtil.exists(arguments, arg -> context.getType(arg) == null);
    if (someArgumentsHaveUnknownType) {
      return StreamEx.of(matchingOverloads)
        .map(overload -> overload.getCallType(context, callSite))
        .collect(PyTypeUtil.toUnion());
    }
    return StreamEx.of(matchingOverloads)
      .findFirst()
      .map(callableType -> callableType.getCallType(context, callSite))
      .orElse(null);
  }

  private static @Nullable PyType clarifyConstructorCallType(@NotNull ClarifiedResolveResult initOrNew,
                                                             @NotNull PyCallSiteExpression callSite,
                                                             @NotNull TypeEvalContext context) {
    final PyFunction initOrNewMethod = (PyFunction)initOrNew.myClarifiedResolved;
    final PyClass initOrNewClass = initOrNewMethod.getContainingClass();

    final PyClass receiverClass = ObjectUtils.notNull(
      PyUtil.as(initOrNew.myOriginalResolveResult.getElement(), PyClass.class),
      Objects.requireNonNull(initOrNewClass)
    );

    final PyType initOrNewCallType = initOrNewMethod.getCallType(context, callSite);
    if (receiverClass != initOrNewClass) {
      if (initOrNewCallType instanceof PyTupleType tupleType) {
        return new PyTupleType(receiverClass, tupleType.getElementTypes(), tupleType.isHomogeneous());
      }

      if (initOrNewCallType instanceof PyCollectionType) {
        final List<PyType> elementTypes = ((PyCollectionType)initOrNewCallType).getElementTypes();
        return new PyCollectionTypeImpl(receiverClass, false, elementTypes);
      }

      return new PyClassTypeImpl(receiverClass, false);
    }

    if (initOrNewCallType instanceof PyCollectionType) {
      return initOrNewCallType;
    }
    if (initOrNewCallType == null) {
      return PyUnionType.createWeakType(new PyClassTypeImpl(receiverClass, false));
    }

    return null;
  }

  @NotNull
  private static Maybe<PyType> getSuperCallType(@NotNull PyCallExpression call, @NotNull TypeEvalContext context) {
    final PyExpression callee = call.getCallee();
    if (callee instanceof PyReferenceExpression) {
      PsiElement must_be_super = ((PyReferenceExpression)callee).getReference().resolve();
      if (must_be_super == PyBuiltinCache.getInstance(call).getClass(PyNames.SUPER)) {
        final PyArgumentList argumentList = call.getArgumentList();
        if (argumentList != null) {
          final PyClass containingClass = PsiTreeUtil.getParentOfType(call, PyClass.class);
          PyExpression[] args = argumentList.getArguments();
          if (containingClass != null && args.length > 1) {
            PyExpression first_arg = args[0];
            if (first_arg instanceof PyReferenceExpression firstArgRef) {
              final PyExpression qualifier = firstArgRef.getQualifier();
              if (qualifier != null && PyNames.__CLASS__.equals(firstArgRef.getReferencedName())) {
                final PsiReference qRef = qualifier.getReference();
                final PsiElement element = qRef == null ? null : qRef.resolve();
                if (element instanceof PyParameter) {
                  final PyParameterList parameterList = PsiTreeUtil.getParentOfType(element, PyParameterList.class);
                  if (parameterList != null && element == parameterList.getParameters()[0]) {
                    return new Maybe<>(getSuperCallTypeForArguments(context, containingClass, args[1]));
                  }
                }
              }
              PsiElement possible_class = firstArgRef.getReference().resolve();
              if (possible_class instanceof PyClass first_class && first_class.isNewStyleClass(context)) {
                return new Maybe<>(getSuperCallTypeForArguments(context, first_class, args[1]));
              }
            }
          }
          else if ((call.getContainingFile() instanceof PyFile) &&
                   ((PyFile)call.getContainingFile()).getLanguageLevel().isPy3K() &&
                   (containingClass != null)) {
            return new Maybe<>(getSuperClassUnionType(containingClass, context));
          }
        }
      }
    }
    return new Maybe<>();
  }

  @Nullable
  private static PyType getSuperCallTypeForArguments(@NotNull TypeEvalContext context,
                                                     @NotNull PyClass firstClass,
                                                     @Nullable PyExpression second_arg) {
    // check 2nd argument, too; it should be an instance
    if (second_arg != null) {
      PyType second_type = context.getType(second_arg);
      if (second_type instanceof PyClassType) {
        // imitate isinstance(second_arg, possible_class)
        PyClass secondClass = ((PyClassType)second_type).getPyClass();
        if (CompletionUtilCoreImpl.getOriginalOrSelf(firstClass) == secondClass) {
          return getSuperClassUnionType(firstClass, context);
        }
        if (secondClass.isSubclass(firstClass, context)) {
          final PyClass nextAfterFirstInMro = StreamEx
            .of(secondClass.getAncestorClasses(context))
            .dropWhile(it -> it != firstClass)
            .skip(1)
            .findFirst()
            .orElse(null);

          if (nextAfterFirstInMro != null) {
            return new PyClassTypeImpl(nextAfterFirstInMro, false);
          }
        }
      }
    }
    return null;
  }

  @Nullable
  private static PyType getSuperClassUnionType(@NotNull PyClass pyClass, TypeEvalContext context) {
    // TODO: this is closer to being correct than simply taking first superclass type but still not entirely correct;
    // super can also delegate to sibling types
    // TODO handle __mro__ here
    final PyClass[] supers = pyClass.getSuperClasses(context);
    if (supers.length > 0) {
      if (supers.length == 1) {
        return new PyClassTypeImpl(supers[0], false);
      }
      List<PyType> superTypes = new ArrayList<>();
      for (PyClass aSuper : supers) {
        superTypes.add(new PyClassTypeImpl(aSuper, false));
      }
      return PyUnionType.union(superTypes);
    }
    return null;
  }

  /**
   * {@code argument} can be (parenthesized) expression or a value of a {@link PyKeywordArgument}
   */
  @ApiStatus.Internal
  @Nullable
  public static List<PyCallableParameter> getMappedParameters(@NotNull PyExpression argument,
                                                              @NotNull PyResolveContext resolveContext) {
    while (argument.getParent() instanceof PyParenthesizedExpression parenthesizedExpr) {
      argument = parenthesizedExpr;
    }

    if (argument.getParent() instanceof PyKeywordArgument keywordArgument) {
      assert keywordArgument.getValueExpression() == argument;
      argument = keywordArgument;
    }

    PsiElement parent = argument.getParent();
    if (parent instanceof PyArgumentList) {
      parent = parent.getParent();
    }
    if (!(parent instanceof PyCallSiteExpression callSite)) {
      return null;
    }

    PyExpression finalArgument = argument;
    return ContainerUtil.mapNotNull(mapArguments(callSite, resolveContext), mapping -> mapping.getMappedParameters().get(finalArgument));
  }

  /**
   * Gets implicit offset from the {@code callableType},
   * should be used with the methods below since they specify correct offset value.
   *
   * @see PyCallExpression#multiResolveCalleeFunction(PyResolveContext)
   * @see PyCallExpression#multiResolveCallee(PyResolveContext)
   */
  @NotNull
  public static PyCallExpression.PyArgumentsMapping mapArguments(@NotNull PyCallSiteExpression callSite,
                                                                 @NotNull PyCallableType callableType,
                                                                 @NotNull TypeEvalContext context) {
    return mapArguments(callSite, callSite.getArguments(callableType.getCallable()), callableType, context);
  }

  @NotNull
  private static PyCallExpression.PyArgumentsMapping mapArguments(@NotNull PyCallSiteExpression callSite,
                                                                  @NotNull List<PyExpression> arguments,
                                                                  @NotNull PyCallableType callableType,
                                                                  @NotNull TypeEvalContext context) {
    final List<PyCallableParameter> parameters = callableType.getParameters(context);
    if (parameters == null) return PyCallExpression.PyArgumentsMapping.empty(callSite);

    final int safeImplicitOffset = Math.min(callableType.getImplicitOffset(), parameters.size());
    final List<PyCallableParameter> explicitParameters = parameters.subList(safeImplicitOffset, parameters.size());
    final List<PyCallableParameter> implicitParameters = parameters.subList(0, safeImplicitOffset);
    final ArgumentMappingResults mappingResults = analyzeArguments(arguments, explicitParameters, context);

    return new PyCallExpression.PyArgumentsMapping(callSite,
                                                   callableType,
                                                   implicitParameters,
                                                   mappingResults.getMappedParameters(),
                                                   mappingResults.getUnmappedParameters(),
                                                   mappingResults.getUnmappedContainerParameters(),
                                                   mappingResults.getUnmappedArguments(),
                                                   mappingResults.getParametersMappedToVariadicPositionalArguments(),
                                                   mappingResults.getParametersMappedToVariadicKeywordArguments(),
                                                   mappingResults.getMappedTupleParameters());
  }

  @NotNull
  public static List<PyCallExpression.@NotNull PyArgumentsMapping> mapArguments(@NotNull PyCallSiteExpression callSite,
                                                                                @NotNull PyResolveContext resolveContext) {
    final TypeEvalContext context = resolveContext.getTypeEvalContext();
    return ContainerUtil.map(multiResolveCalleeFunction(callSite, resolveContext), type -> mapArguments(callSite, type, context));
  }

  @NotNull
  private static List<@NotNull PyCallableType> multiResolveCalleeFunction(@NotNull PyCallSiteExpression callSite,
                                                                          @NotNull PyResolveContext resolveContext) {
    if (callSite instanceof PyCallExpression) {
      return ((PyCallExpression)callSite).multiResolveCallee(resolveContext);
    }
    else if (callSite instanceof PySubscriptionExpression) {
      return multiResolveCallee((PyReferenceOwner)callSite, resolveContext);
    }
    else {
      final List<PyCallableType> results = new ArrayList<>();

      for (PsiElement result : PyUtil.multiResolveTopPriority(callSite, resolveContext)) {
        if (result instanceof PyTypedElement) {
          final PyType resultType = resolveContext.getTypeEvalContext().getType((PyTypedElement)result);
          if (resultType instanceof PyCallableType) {
            results.add((PyCallableType)resultType);
            continue;
          }
        }
        return Collections.emptyList();
      }

      return results;
    }
  }

  /**
   * Tries to infer implicit offset from the {@code callSite} and {@code callable}.
   *
   * @see PyCallExpressionHelper#mapArguments(PyCallSiteExpression, PyCallableType, TypeEvalContext)
   */
  @NotNull
  public static PyCallExpression.PyArgumentsMapping mapArguments(@NotNull PyCallSiteExpression callSite,
                                                                 @NotNull PyCallable callable,
                                                                 @NotNull TypeEvalContext context) {
    final PyCallableType callableType = PyUtil.as(context.getType(callable), PyCallableType.class);
    if (callableType == null) return PyCallExpression.PyArgumentsMapping.empty(callSite);

    final List<PyCallableParameter> parameters = callableType.getParameters(context);
    if (parameters == null) return PyCallExpression.PyArgumentsMapping.empty(callSite);

    final PyResolveContext resolveContext = PyResolveContext.defaultContext(context);
    final List<PyExpression> arguments = callSite.getArguments(callable);
    final List<PyCallableParameter> explicitParameters = filterExplicitParameters(parameters, callable, callSite, resolveContext);
    final List<PyCallableParameter> implicitParameters = parameters.subList(0, parameters.size() - explicitParameters.size());

    final ArgumentMappingResults mappingResults = analyzeArguments(arguments, explicitParameters, context);

    return new PyCallExpression.PyArgumentsMapping(callSite,
                                                   callableType,
                                                   implicitParameters,
                                                   mappingResults.getMappedParameters(),
                                                   mappingResults.getUnmappedParameters(),
                                                   mappingResults.getUnmappedContainerParameters(),
                                                   mappingResults.getUnmappedArguments(),
                                                   mappingResults.getParametersMappedToVariadicPositionalArguments(),
                                                   mappingResults.getParametersMappedToVariadicKeywordArguments(),
                                                   mappingResults.getMappedTupleParameters());
  }

  @NotNull
  public static <T> List<T> getArgumentsMappedToPositionalContainer(@NotNull Map<T, PyCallableParameter> mapping) {
    return StreamEx.ofKeys(mapping, PyCallableParameter::isPositionalContainer).toList();
  }

  @NotNull
  public static <T> List<T> getArgumentsMappedToKeywordContainer(@NotNull Map<T, PyCallableParameter> mapping) {
    return StreamEx.ofKeys(mapping, PyCallableParameter::isKeywordContainer).toList();
  }

  @NotNull
  public static <T> Map<T, PyCallableParameter> getRegularMappedParameters(@NotNull Map<T, PyCallableParameter> mapping) {
    final Map<T, PyCallableParameter> result = new LinkedHashMap<>();
    for (Map.Entry<T, PyCallableParameter> entry : mapping.entrySet()) {
      final T argument = entry.getKey();
      final PyCallableParameter parameter = entry.getValue();
      if (!parameter.isPositionalContainer() && !parameter.isKeywordContainer()) {
        result.put(argument, parameter);
      }
    }
    return result;
  }

  @Nullable
  public static <T> PyCallableParameter getMappedPositionalContainer(@NotNull Map<T, PyCallableParameter> mapping) {
    return ContainerUtil.find(mapping.values(), p -> p.isPositionalContainer());
  }

  @Nullable
  public static <T> PyCallableParameter getMappedKeywordContainer(@NotNull Map<T, PyCallableParameter> mapping) {
    return ContainerUtil.find(mapping.values(), p -> p.isKeywordContainer());
  }

  public static @NotNull List<? extends RatedResolveResult> resolveImplicitlyInvokedMethods(@NotNull PyClassType type,
                                                                                            @Nullable PyCallSiteExpression callSite,
                                                                                            @NotNull PyResolveContext resolveContext) {
    return type.isDefinition() ? resolveConstructors(type, callSite, resolveContext) : resolveDunderCall(type, callSite, resolveContext);
  }

  private static @NotNull List<@NotNull PyCallableType> changeToImplicitlyInvokedMethods(@NotNull PyClassType classType,
                                                                                         @NotNull List<PsiElement> implicitlyInvokedMethods,
                                                                                         @NotNull PyCallExpression call,
                                                                                         @NotNull TypeEvalContext context) {
    final var cls = classType.getPyClass();
    return StreamEx
      .of(implicitlyInvokedMethods)
      .map(
        it ->
          new ClarifiedResolveResult(
            new QualifiedRatedResolveResult(cls, Collections.emptyList(), RatedResolveResult.RATE_NORMAL, false),
            it,
            null,
            PyUtil.isInitOrNewMethod(it)
          )
      )
      .map(it -> toCallableType(call, it, context))
      .nonNull()
      .toList();
  }

  @NotNull
  private static List<? extends RatedResolveResult> resolveConstructors(@NotNull PyClassType type,
                                                                        @Nullable PyExpression location,
                                                                        @NotNull PyResolveContext resolveContext) {
    final var metaclassDunderCall = resolveMetaclassDunderCall(type, location, resolveContext);
    if (!metaclassDunderCall.isEmpty()) {
      return metaclassDunderCall;
    }

    final var context = resolveContext.getTypeEvalContext();
    final var initAndNew = type.getPyClass().multiFindInitOrNew(true, context);
    return ContainerUtil.map(preferInitOverNew(initAndNew), e -> new RatedResolveResult(PyReferenceImpl.getRate(e, context), e));
  }

  @NotNull
  private static Collection<? extends PyFunction> preferInitOverNew(@NotNull List<PyFunction> initAndNew) {
    final MultiMap<String, PyFunction> functions = ContainerUtil.groupBy(initAndNew, PyFunction::getName);
    return functions.containsKey(PyNames.INIT) ? functions.get(PyNames.INIT) : functions.values();
  }

  @NotNull
  private static List<? extends RatedResolveResult> resolveMetaclassDunderCall(@NotNull PyClassType type,
                                                                               @Nullable PyExpression location,
                                                                               @NotNull PyResolveContext resolveContext) {
    final var context = resolveContext.getTypeEvalContext();

    final PyClassLikeType metaClassType = type.getMetaClassType(context, true);
    if (metaClassType == null) return Collections.emptyList();

    final PyClassType typeType = PyBuiltinCache.getInstance(type.getPyClass()).getTypeType();
    if (metaClassType == typeType) return Collections.emptyList();

    final var results = resolveDunderCall(metaClassType, location, resolveContext);
    if (results.isEmpty()) return Collections.emptyList();

    final Set<PsiElement> typeDunderCall =
      typeType == null
      ? Collections.emptySet()
      : ContainerUtil.map2SetNotNull(resolveDunderCall(typeType, null, resolveContext), RatedResolveResult::getElement);

    return ContainerUtil.filter(
      results,
      it -> {
        final var element = it.getElement();
        return !typeDunderCall.contains(element) && !ParamHelper.isSelfArgsKwargsCallable(element, context);
      }
    );
  }

  @NotNull
  private static List<? extends RatedResolveResult> resolveDunderCall(@NotNull PyClassLikeType type,
                                                                      @Nullable PyExpression location,
                                                                      @NotNull PyResolveContext resolveContext) {
    return ContainerUtil.notNullize(type.resolveMember(PyNames.CALL, location, AccessDirection.READ, resolveContext));
  }

  @NotNull
  public static ArgumentMappingResults analyzeArguments(@NotNull List<PyExpression> arguments,
                                                         @NotNull List<PyCallableParameter> parameters,
                                                         @NotNull TypeEvalContext context) {
    boolean positionalOnlyMode = ContainerUtil.exists(parameters, p -> p.getParameter() instanceof PySlashParameter);
    boolean seenSingleStar = false;
    boolean mappedVariadicArgumentsToParameters = false;
    final Map<PyExpression, PyCallableParameter> mappedParameters = new LinkedHashMap<>();
    final List<PyCallableParameter> unmappedParameters = new ArrayList<>();
    final List<PyCallableParameter> unmappedContainerParameters = new ArrayList<>();
    final List<PyExpression> unmappedArguments = new ArrayList<>();
    final List<PyCallableParameter> parametersMappedToVariadicKeywordArguments = new ArrayList<>();
    final List<PyCallableParameter> parametersMappedToVariadicPositionalArguments = new ArrayList<>();
    final Map<PyExpression, PyCallableParameter> tupleMappedParameters = new LinkedHashMap<>();

    final PositionalArgumentsAnalysisResults positionalResults = filterPositionalAndVariadicArguments(arguments);
    final List<PyKeywordArgument> keywordArguments = filterKeywordArguments(arguments);
    final List<PyExpression> variadicPositionalArguments = positionalResults.variadicPositionalArguments;
    final Set<PyExpression> positionalComponentsOfVariadicArguments =
      new LinkedHashSet<>(positionalResults.componentsOfVariadicPositionalArguments);
    final List<PyExpression> variadicKeywordArguments = filterVariadicKeywordArguments(arguments);

    final List<PyExpression> allPositionalArguments = positionalResults.allPositionalArguments;

    for (PyCallableParameter parameter : parameters) {
      final PyParameter psi = parameter.getParameter();

      if (psi instanceof PyNamedParameter || psi == null) {
        final String parameterName = parameter.getName();
        if (parameter.isPositionalContainer()) {
          for (PyExpression argument : allPositionalArguments) {
            if (argument != null) {
              mappedParameters.put(argument, parameter);
            }
          }
          if (variadicPositionalArguments.size() == 1) {
            mappedParameters.put(variadicPositionalArguments.get(0), parameter);
          }
          if (variadicPositionalArguments.size() != 1 && allPositionalArguments.size() == 0) {
            unmappedContainerParameters.add(parameter);
          }
          allPositionalArguments.clear();
          variadicPositionalArguments.clear();
        }
        else if (parameter.isKeywordContainer()) {
          for (PyKeywordArgument argument : keywordArguments) {
            mappedParameters.put(argument, parameter);
          }
          for (PyExpression variadicKeywordArg : variadicKeywordArguments) {
            mappedParameters.put(variadicKeywordArg, parameter);
          }
          keywordArguments.clear();
          variadicKeywordArguments.clear();
        }
        else if (seenSingleStar) {
          final PyExpression keywordArgument = removeKeywordArgument(keywordArguments, parameterName);
          if (keywordArgument != null) {
            mappedParameters.put(keywordArgument, parameter);
          }
          else if (variadicKeywordArguments.isEmpty()) {
            if (!parameter.hasDefaultValue()) {
              unmappedParameters.add(parameter);
            }
          }
          else {
            parametersMappedToVariadicKeywordArguments.add(parameter);
            mappedVariadicArgumentsToParameters = true;
          }
        }
        else if (isParamSpecOrConcatenate(parameter, context)) {
          for (var argument: arguments) {
            mappedParameters.put(argument, parameter);
          }
          allPositionalArguments.clear();
          keywordArguments.clear();
          variadicPositionalArguments.clear();
          variadicKeywordArguments.clear();
        }
        else {
          if (positionalOnlyMode) {
            final PyExpression positionalArgument = next(allPositionalArguments);

            if (positionalArgument != null) {
              mappedParameters.put(positionalArgument, parameter);
            }
            else if (!parameter.hasDefaultValue()) {
              unmappedParameters.add(parameter);
            }
          }
          else if (allPositionalArguments.isEmpty()) {
            final PyKeywordArgument keywordArgument = removeKeywordArgument(keywordArguments, parameterName);
            if (keywordArgument != null) {
              mappedParameters.put(keywordArgument, parameter);
            }
            else if (variadicPositionalArguments.isEmpty() && variadicKeywordArguments.isEmpty() && !parameter.hasDefaultValue()) {
              unmappedParameters.add(parameter);
            }
            else {
              if (!variadicPositionalArguments.isEmpty()) {
                parametersMappedToVariadicPositionalArguments.add(parameter);
              }
              if (!variadicKeywordArguments.isEmpty()) {
                parametersMappedToVariadicKeywordArguments.add(parameter);
              }
              mappedVariadicArgumentsToParameters = true;
            }
          }
          else {
            final PyExpression positionalArgument = next(allPositionalArguments);
            if (positionalArgument != null) {
              mappedParameters.put(positionalArgument, parameter);
              if (positionalComponentsOfVariadicArguments.contains(positionalArgument)) {
                parametersMappedToVariadicPositionalArguments.add(parameter);
              }
            }
            else if (!parameter.hasDefaultValue()) {
              unmappedParameters.add(parameter);
            }
          }
        }
      }
      else if (psi instanceof PyTupleParameter) {
        final PyExpression positionalArgument = next(allPositionalArguments);
        if (positionalArgument != null) {
          tupleMappedParameters.put(positionalArgument, parameter);
          final TupleMappingResults tupleMappingResults = mapComponentsOfTupleParameter(positionalArgument, (PyTupleParameter)psi);
          mappedParameters.putAll(tupleMappingResults.getParameters());
          unmappedParameters.addAll(tupleMappingResults.getUnmappedParameters());
          unmappedArguments.addAll(tupleMappingResults.getUnmappedArguments());
        }
        else if (variadicPositionalArguments.isEmpty()) {
          if (!parameter.hasDefaultValue()) {
            unmappedParameters.add(parameter);
          }
        }
        else {
          mappedVariadicArgumentsToParameters = true;
        }
      }
      else if (psi instanceof PySlashParameter) {
        positionalOnlyMode = false;
      }
      else if (psi instanceof PySingleStarParameter) {
        seenSingleStar = true;
      }
      else if (!parameter.hasDefaultValue()) {
        unmappedParameters.add(parameter);
      }
    }

    if (mappedVariadicArgumentsToParameters) {
      variadicPositionalArguments.clear();
      variadicKeywordArguments.clear();
    }

    unmappedArguments.addAll(allPositionalArguments);
    unmappedArguments.addAll(keywordArguments);
    unmappedArguments.addAll(variadicPositionalArguments);
    unmappedArguments.addAll(variadicKeywordArguments);

    return new ArgumentMappingResults(mappedParameters, unmappedParameters, unmappedContainerParameters, unmappedArguments,
                                      parametersMappedToVariadicPositionalArguments, parametersMappedToVariadicKeywordArguments,
                                      tupleMappedParameters);
  }

  private static boolean isParamSpecOrConcatenate(@NotNull PyCallableParameter parameter, @NotNull TypeEvalContext context) {
    final var type = parameter.getType(context);
    return type instanceof PyParamSpecType || type instanceof PyConcatenateType;
  }

  @NotNull
  private static List<PsiElement> forEveryScopeTakeOverloadsOtherwiseImplementations(@NotNull List<? extends ResolveResult> results,
                                                                                     @NotNull TypeEvalContext context) {
    return PyUtil.filterTopPriorityElements(
      forEveryScopeTakeOverloadsOtherwiseImplementations(results, ResolveResult::getElement, context)
    );
  }

  @NotNull
  private static <E extends ResolveResult> List<E> forEveryScopeTakeOverloadsOtherwiseImplementations(
    @NotNull List<E> elements,
    @NotNull Function<? super E, PsiElement> mapper,
    @NotNull TypeEvalContext context
  ) {
    if (!containsOverloadsAndImplementations(elements, mapper, context)) {
      return elements;
    }

    return StreamEx
      .of(elements)
      .groupingBy(element -> Optional.ofNullable(ScopeUtil.getScopeOwner(mapper.apply(element))), LinkedHashMap::new, Collectors.toList())
      .values()
      .stream()
      .flatMap(oneScopeElements -> takeOverloadsOtherwiseImplementations(oneScopeElements, mapper, context))
      .collect(Collectors.toList());
  }

  private static <E extends ResolveResult> boolean containsOverloadsAndImplementations(@NotNull Collection<E> elements,
                                                                                       @NotNull Function<? super E, PsiElement> mapper,
                                                                                       @NotNull TypeEvalContext context) {
    boolean containsOverloads = false;
    boolean containsImplementations = false;

    for (E element : elements) {
      final PsiElement mapped = mapper.apply(element);
      if (mapped == null) continue;

      final boolean overload = PyiUtil.isOverload(mapped, context);
      containsOverloads |= overload;
      containsImplementations |= !overload;

      if (containsOverloads && containsImplementations) return true;
    }

    return false;
  }

  @NotNull
  private static <E extends ResolveResult> Stream<E> takeOverloadsOtherwiseImplementations(@NotNull List<E> elements,
                                                                                           @NotNull Function<? super E, PsiElement> mapper,
                                                                                           @NotNull TypeEvalContext context) {
    if (!containsOverloadsAndImplementations(elements, mapper, context)) {
      return elements.stream();
    }

    return elements
      .stream()
      .filter(
        element -> {
          final PsiElement mapped = mapper.apply(element);
          return mapped != null && (PyiUtil.isInsideStub(mapped) || PyiUtil.isOverload(mapped, context));
        }
      );
  }

  private static boolean matchesByArgumentTypes(@NotNull PyFunction callable,
                                                @NotNull PyCallSiteExpression callSite,
                                                @NotNull TypeEvalContext context) {
    final PyCallExpression.PyArgumentsMapping fullMapping = mapArguments(callSite, callable, context);
    if (!fullMapping.isComplete()) return false;

    // TODO properly handle bidirectional operator methods, such as __eq__ and __neq__. 
    //  Based only on its name, it's impossible to which operand is the receiver and which one is the argument. 
    final PyExpression receiver = callSite.getReceiver(callable);
    final Map<PyExpression, PyCallableParameter> mappedExplicitParameters = fullMapping.getMappedParameters();

    final Map<PyExpression, PyCallableParameter> allMappedParameters = new LinkedHashMap<>();
    final PyCallableParameter firstImplicit = ContainerUtil.getFirstItem(fullMapping.getImplicitParameters());
    if (receiver != null && firstImplicit != null) {
      allMappedParameters.put(receiver, firstImplicit);
    }
    allMappedParameters.putAll(mappedExplicitParameters);

    return PyTypeChecker.unifyGenericCall(receiver, allMappedParameters, context) != null;
  }

  public static class ArgumentMappingResults {
    @NotNull private final Map<PyExpression, PyCallableParameter> myMappedParameters;
    @NotNull private final List<PyCallableParameter> myUnmappedParameters;
    @NotNull private final List<PyCallableParameter> myUnmappedContainerParameters;
    @NotNull private final List<PyExpression> myUnmappedArguments;
    @NotNull private final List<PyCallableParameter> myParametersMappedToVariadicPositionalArguments;
    @NotNull private final List<PyCallableParameter> myParametersMappedToVariadicKeywordArguments;
    @NotNull private final Map<PyExpression, PyCallableParameter> myMappedTupleParameters;

    ArgumentMappingResults(@NotNull Map<PyExpression, PyCallableParameter> mappedParameters,
                           @NotNull List<PyCallableParameter> unmappedParameters,
                           @NotNull List<PyCallableParameter> unmappedContainerParameters,
                           @NotNull List<PyExpression> unmappedArguments,
                           @NotNull List<PyCallableParameter> parametersMappedToVariadicPositionalArguments,
                           @NotNull List<PyCallableParameter> parametersMappedToVariadicKeywordArguments,
                           @NotNull Map<PyExpression, PyCallableParameter> mappedTupleParameters) {
      myMappedParameters = mappedParameters;
      myUnmappedParameters = unmappedParameters;
      myUnmappedContainerParameters = unmappedContainerParameters;
      myUnmappedArguments = unmappedArguments;
      myParametersMappedToVariadicPositionalArguments = parametersMappedToVariadicPositionalArguments;
      myParametersMappedToVariadicKeywordArguments = parametersMappedToVariadicKeywordArguments;
      myMappedTupleParameters = mappedTupleParameters;
    }

    @NotNull
    public Map<PyExpression, PyCallableParameter> getMappedParameters() {
      return myMappedParameters;
    }

    @NotNull
    public List<PyCallableParameter> getUnmappedParameters() {
      return myUnmappedParameters;
    }

    @NotNull
    public List<PyExpression> getUnmappedArguments() {
      return myUnmappedArguments;
    }

    @NotNull
    public List<PyCallableParameter> getParametersMappedToVariadicPositionalArguments() {
      return myParametersMappedToVariadicPositionalArguments;
    }

    @NotNull
    public List<PyCallableParameter> getParametersMappedToVariadicKeywordArguments() {
      return myParametersMappedToVariadicKeywordArguments;
    }

    @NotNull
    public Map<PyExpression, PyCallableParameter> getMappedTupleParameters() {
      return myMappedTupleParameters;
    }

    @NotNull
    public List<PyCallableParameter> getUnmappedContainerParameters() {
      return myUnmappedContainerParameters;
    }
  }

  private static class TupleMappingResults {
    @NotNull private final Map<PyExpression, PyCallableParameter> myParameters;
    @NotNull private final List<PyCallableParameter> myUnmappedParameters;
    @NotNull private final List<PyExpression> myUnmappedArguments;

    TupleMappingResults(@NotNull Map<PyExpression, PyCallableParameter> mappedParameters,
                        @NotNull List<PyCallableParameter> unmappedParameters,
                        @NotNull List<PyExpression> unmappedArguments) {

      myParameters = mappedParameters;
      myUnmappedParameters = unmappedParameters;
      myUnmappedArguments = unmappedArguments;
    }

    @NotNull
    public Map<PyExpression, PyCallableParameter> getParameters() {
      return myParameters;
    }

    @NotNull
    public List<PyCallableParameter> getUnmappedParameters() {
      return myUnmappedParameters;
    }

    @NotNull
    public List<PyExpression> getUnmappedArguments() {
      return myUnmappedArguments;
    }
  }

  @NotNull
  private static TupleMappingResults mapComponentsOfTupleParameter(@Nullable PyExpression argument, @NotNull PyTupleParameter parameter) {
    final List<PyCallableParameter> unmappedParameters = new ArrayList<>();
    final List<PyExpression> unmappedArguments = new ArrayList<>();
    final Map<PyExpression, PyCallableParameter> mappedParameters = new LinkedHashMap<>();
    argument = PyPsiUtils.flattenParens(argument);
    if (argument instanceof PySequenceExpression sequenceExpr) {
      final PyExpression[] argumentComponents = sequenceExpr.getElements();
      final PyParameter[] parameterComponents = parameter.getContents();
      for (int i = 0; i < parameterComponents.length; i++) {
        final PyParameter param = parameterComponents[i];
        if (i < argumentComponents.length) {
          final PyExpression arg = argumentComponents[i];
          if (arg != null) {
            if (param instanceof PyNamedParameter) {
              mappedParameters.put(arg, PyCallableParameterImpl.psi(param));
            }
            else if (param instanceof PyTupleParameter) {
              final TupleMappingResults nestedResults = mapComponentsOfTupleParameter(arg, (PyTupleParameter)param);
              mappedParameters.putAll(nestedResults.getParameters());
              unmappedParameters.addAll(nestedResults.getUnmappedParameters());
              unmappedArguments.addAll(nestedResults.getUnmappedArguments());
            }
            else {
              unmappedArguments.add(arg);
            }
          }
          else {
            unmappedParameters.add(PyCallableParameterImpl.psi(param));
          }
        }
        else {
          unmappedParameters.add(PyCallableParameterImpl.psi(param));
        }
      }
      if (argumentComponents.length > parameterComponents.length) {
        for (int i = parameterComponents.length; i < argumentComponents.length; i++) {
          final PyExpression arg = argumentComponents[i];
          if (arg != null) {
            unmappedArguments.add(arg);
          }
        }
      }
    }
    return new TupleMappingResults(mappedParameters, unmappedParameters, unmappedArguments);
  }

  @Nullable
  private static PyKeywordArgument removeKeywordArgument(@NotNull List<PyKeywordArgument> arguments, @Nullable String name) {
    PyKeywordArgument result = null;
    for (PyKeywordArgument argument : arguments) {
      final String keyword = argument.getKeyword();
      if (keyword != null && keyword.equals(name)) {
        result = argument;
        break;
      }
    }
    if (result != null) {
      arguments.remove(result);
    }
    return result;
  }

  @NotNull
  private static List<PyKeywordArgument> filterKeywordArguments(@NotNull List<PyExpression> arguments) {
    final List<PyKeywordArgument> results = new ArrayList<>();
    for (PyExpression argument : arguments) {
      if (argument instanceof PyKeywordArgument) {
        results.add((PyKeywordArgument)argument);
      }
    }
    return results;
  }

  private static class PositionalArgumentsAnalysisResults {
    @NotNull private final List<PyExpression> allPositionalArguments;
    @NotNull private final List<PyExpression> componentsOfVariadicPositionalArguments;
    @NotNull private final List<PyExpression> variadicPositionalArguments;

    PositionalArgumentsAnalysisResults(@NotNull List<PyExpression> allPositionalArguments,
                                       @NotNull List<PyExpression> componentsOfVariadicPositionalArguments,
                                       @NotNull List<PyExpression> variadicPositionalArguments) {
      this.allPositionalArguments = allPositionalArguments;
      this.componentsOfVariadicPositionalArguments = componentsOfVariadicPositionalArguments;
      this.variadicPositionalArguments = variadicPositionalArguments;
    }
  }

  @NotNull
  private static PositionalArgumentsAnalysisResults filterPositionalAndVariadicArguments(@NotNull List<PyExpression> arguments) {
    final List<PyExpression> variadicArguments = new ArrayList<>();
    final List<PyExpression> allPositionalArguments = new ArrayList<>();
    final List<PyExpression> componentsOfVariadicPositionalArguments = new ArrayList<>();
    boolean seenVariadicPositionalArgument = false;
    boolean seenVariadicKeywordArgument = false;
    boolean seenKeywordArgument = false;
    for (PyExpression argument : arguments) {
      if (argument instanceof PyStarArgument) {
        if (((PyStarArgument)argument).isKeyword()) {
          seenVariadicKeywordArgument = true;
        }
        else {
          seenVariadicPositionalArgument = true;
          final PsiElement expr = PyPsiUtils.flattenParens(PsiTreeUtil.getChildOfType(argument, PyExpression.class));
          if (expr instanceof PySequenceExpression sequenceExpr) {
            final List<PyExpression> elements = Arrays.asList(sequenceExpr.getElements());
            allPositionalArguments.addAll(elements);
            componentsOfVariadicPositionalArguments.addAll(elements);
          }
          else {
            variadicArguments.add(argument);
          }
        }
      }
      else if (argument instanceof PyKeywordArgument) {
        seenKeywordArgument = true;
      }
      else {
        if (seenKeywordArgument ||
            seenVariadicKeywordArgument ||
            seenVariadicPositionalArgument && LanguageLevel.forElement(argument).isOlderThan(LanguageLevel.PYTHON35)) {
          continue;
        }
        allPositionalArguments.add(argument);
      }
    }
    return new PositionalArgumentsAnalysisResults(allPositionalArguments, componentsOfVariadicPositionalArguments, variadicArguments);
  }

  @NotNull
  private static List<PyExpression> filterVariadicKeywordArguments(@NotNull List<PyExpression> arguments) {
    final List<PyExpression> results = new ArrayList<>();
    for (PyExpression argument : arguments) {
      if (argument != null && isVariadicKeywordArgument(argument)) {
        results.add(argument);
      }
    }
    return results;
  }

  public static boolean isVariadicKeywordArgument(@NotNull PyExpression argument) {
    return argument instanceof PyStarArgument && ((PyStarArgument)argument).isKeyword();
  }

  public static boolean isVariadicPositionalArgument(@NotNull PyExpression argument) {
    return argument instanceof PyStarArgument && !((PyStarArgument)argument).isKeyword();
  }

  @Nullable
  private static <T> T next(@NotNull List<T> list) {
    return list.isEmpty() ? null : list.remove(0);
  }

  @NotNull
  private static List<PyCallableParameter> filterExplicitParameters(@NotNull List<PyCallableParameter> parameters,
                                                                    @Nullable PyCallable callable,
                                                                    @NotNull PyCallSiteExpression callSite,
                                                                    @NotNull PyResolveContext resolveContext) {
    final int implicitOffset;
    if (callSite instanceof PyCallExpression callExpr) {
      final PyExpression callee = callExpr.getCallee();
      if (callee instanceof PyReferenceExpression && callable instanceof PyFunction) {
        implicitOffset = getImplicitArgumentCount((PyReferenceExpression)callee, (PyFunction)callable,
                                                  resolveContext);
      }
      else {
        implicitOffset = 0;
      }
    }
    else {
      implicitOffset = 1;
    }
    return parameters.subList(Math.min(implicitOffset, parameters.size()), parameters.size());
  }

  public static boolean canQualifyAnImplicitName(@NotNull PyExpression qualifier) {
    if (qualifier instanceof PyCallExpression) {
      final PyExpression callee = ((PyCallExpression)qualifier).getCallee();
      if (callee instanceof PyReferenceExpression && PyNames.SUPER.equals(callee.getName())) {
        final PsiElement target = ((PyReferenceExpression)callee).getReference().resolve();
        if (target != null && PyBuiltinCache.getInstance(qualifier).isBuiltin(target)) return false; // super() of unresolved type
      }
    }
    return true;
  }

  private static class ClarifiedResolveResult {

    @NotNull
    private final QualifiedRatedResolveResult myOriginalResolveResult;

    @NotNull
    private final PsiElement myClarifiedResolved;

    @Nullable
    private final PyFunction.Modifier myWrappedModifier;

    private final boolean myIsConstructor;

    ClarifiedResolveResult(@NotNull QualifiedRatedResolveResult originalResolveResult,
                           @NotNull PsiElement clarifiedResolved,
                           @Nullable PyFunction.Modifier wrappedModifier,
                           boolean isConstructor) {
      myOriginalResolveResult = originalResolveResult;
      myClarifiedResolved = clarifiedResolved;
      myWrappedModifier = wrappedModifier;
      myIsConstructor = isConstructor;
    }
  }
}
