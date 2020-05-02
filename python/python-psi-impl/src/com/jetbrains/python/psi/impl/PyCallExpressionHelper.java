// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.psi.impl;

import com.intellij.codeInsight.completion.CompletionUtilCoreImpl;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiPolyVariantReference;
import com.intellij.psi.PsiReference;
import com.intellij.psi.ResolveResult;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.codeInsight.dataflow.scope.ScopeUtil;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.references.PyQualifiedReference;
import com.jetbrains.python.psi.resolve.*;
import com.jetbrains.python.psi.types.*;
import com.jetbrains.python.pyi.PyiUtil;
import com.jetbrains.python.toolbox.Maybe;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Functions common to different implementors of PyCallExpression, with different base classes.
 * User: dcheryasov
 */
public class PyCallExpressionHelper {
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
    if (callee instanceof PyReferenceExpression) {
      // dereference
      PyReferenceExpression ref = (PyReferenceExpression)callee;
      resolveResult = ref.followAssignmentsChain(PyResolveContext.defaultContext());
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
      final PyFunction pyFunction = (PyFunction)resolved;
      return pyFunction.getContainingClass();
    }

    return null;
  }

  @NotNull
  public static List<PyCallableType> multiResolveCallee(@NotNull PyCallExpression call,
                                                        @NotNull PyResolveContext resolveContext,
                                                        int implicitOffset) {
    final PyExpression callee = call.getCallee();
    final TypeEvalContext context = resolveContext.getTypeEvalContext();

    final List<PyCallableType> calleesFromProviders = getCalleesFromProviders(callee, context);
    if (calleesFromProviders != null) {
      return calleesFromProviders;
    }

    if (callee == null) return Collections.emptyList();

    return Stream.concat(getExplicitResolveResults(callee, call, context, implicitOffset, resolveContext),
                         getImplicitResolveResults(callee, implicitOffset, resolveContext)).collect(Collectors.toList());
  }

  @NotNull
  private static Stream<PyCallableType> getExplicitResolveResults(@NotNull PyExpression callee,
                                                                  @NotNull PyCallExpression call,
                                                                  @NotNull TypeEvalContext context,
                                                                  int implicitOffset,
                                                                  @NotNull PyResolveContext resolveContext) {
    final PyType callableTypes = context.getType(callee);
    final List<PsiElement> dunderCallFunctions = resolveDunderCallMembers(callableTypes, call, resolveContext);

    return !dunderCallFunctions.isEmpty() ? selectCallableTypes(StreamEx.of(dunderCallFunctions), context)
                                          : addImplicitOffset(PyTypeUtil.toStream(callableTypes).select(PyCallableType.class),
                                                              implicitOffset, context);
  }

  @NotNull
  private static Stream<PyCallableType> getImplicitResolveResults(@NotNull PyExpression callee, int implicitOffset,
                                                                  @NotNull PyResolveContext resolveContext) {
    ResolveResultList implicitResolveResults = new ResolveResultList();
    TypeEvalContext context = resolveContext.getTypeEvalContext();
    if (resolveContext.allowImplicits()) {
      if (callee instanceof PyReferenceOwner && callee instanceof PyQualifiedExpression) {
        final PsiPolyVariantReference reference = ((PyReferenceOwner)callee).getReference(resolveContext);
        if (reference instanceof PyQualifiedReference && reference.getElement() instanceof PyQualifiedExpression) {
          final PyQualifiedExpression referenceElement = (PyQualifiedExpression)reference.getElement();
          final PyExpression qualifier = referenceElement.getQualifier();
          if (qualifier != null) {
            final PyType qualifierType = context.getType(qualifier);
            if ((PyTypeChecker.isUnknown(qualifierType, context) ||
                 (qualifierType instanceof PyStructuralType && ((PyStructuralType)qualifierType).isInferredFromUsages())) &&
                resolveContext.allowImplicits() && canQualifyAnImplicitName(qualifier)) {
              PyResolveUtil
                .addImplicitResolveResults(((PyQualifiedExpression)callee).getReferencedName(), implicitResolveResults, referenceElement);
            }
          }
        }
      }
    }
    return addImplicitOffset(selectCallableTypes(StreamEx.of(implicitResolveResults).map(ResolveResult::getElement), context),
                             implicitOffset, context);
  }

  @NotNull
  private static StreamEx<PyCallableType> selectCallableTypes(@NotNull StreamEx<PsiElement> resolveResults,
                                                              @NotNull TypeEvalContext context) {
    return resolveResults
      .select(PyTypedElement.class)
      .map(element -> context.getType(element))
      .select(PyCallableType.class);
  }

  @NotNull
  private static List<PsiElement> resolveDunderCallMembers(@Nullable PyType callableTypes, @NotNull PyCallExpression call,
                                                           @NotNull PyResolveContext resolveContext) {
    return PyTypeUtil.toStream(callableTypes)
      .select(PyClassLikeType.class)
      .filter(type -> !type.isDefinition())
      .map(type -> type.resolveMember(PyNames.CALL, call, AccessDirection.READ, resolveContext, true))
      .nonNull()
      .flatMap(list -> StreamEx.of(list).map(RatedResolveResult::getElement).nonNull())
      .nonNull()
      .toList();
  }

  @NotNull
  private static Stream<PyCallableType> addImplicitOffset(@NotNull Stream<PyCallableType> callableTypeStream, int implicitOffset,
                                                          @NotNull TypeEvalContext context) {
    return implicitOffset == 0
           ? callableTypeStream
           : callableTypeStream.map(callableType -> new PyCallableTypeImpl(callableType.getParameters(context),
                                                                           callableType.getReturnType(context),
                                                                           callableType.getCallable(),
                                                                           callableType.getModifier(),
                                                                           callableType.getImplicitOffset() + implicitOffset));
  }

  @Nullable
  private static List<PyCallableType> getCalleesFromProviders(@Nullable PyExpression callee,
                                                              @NotNull TypeEvalContext context) {
    if (callee instanceof PyReferenceExpression) {
      final PyReferenceExpression referenceExpression = (PyReferenceExpression)callee;

      final List<PyCallableType> callableTypes = StreamEx
        .of(PyTypeProvider.EP_NAME.getExtensionList())
        .map(provider -> provider.getReferenceExpressionType(referenceExpression, context))
        .select(PyCallableType.class)
        .toList();

      if (!callableTypes.isEmpty()) {
        return callableTypes;
      }
    }

    return null;
  }

  @NotNull
  public static List<QualifiedRatedResolveResult> multiResolveCallee(@Nullable PyExpression callee,
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
  public static List<ClarifiedResolveResult> clarifyResolveResult(@NotNull PyCallExpression call,
                                                                  @NotNull QualifiedRatedResolveResult resolveResult,
                                                                  @NotNull PyResolveContext resolveContext) {
    final PsiElement resolved = resolveResult.getElement();

    if (resolved instanceof PyClass) {
      return ContainerUtil.map(resolveConstructors((PyClass)resolved, call, resolveContext.getTypeEvalContext(), true),
                               function -> new ClarifiedResolveResult(resolveResult, function, null, true));
    }
    else if (resolved instanceof PyCallExpression) { // foo = classmethod(foo)
      final PyCallExpression resolvedCall = (PyCallExpression)resolved;

      final Pair<String, PyFunction> wrapperInfo = interpretAsModifierWrappingCall(resolvedCall);
      if (wrapperInfo != null) {
        final String wrapperName = wrapperInfo.getFirst();
        final PyFunction.Modifier wrappedModifier = PyNames.CLASSMETHOD.equals(wrapperName)
                                                    ? PyFunction.Modifier.CLASSMETHOD
                                                    : PyNames.STATICMETHOD.equals(wrapperName)
                                                      ? PyFunction.Modifier.STATICMETHOD
                                                      : null;

        final ClarifiedResolveResult result = new ClarifiedResolveResult(resolveResult, wrapperInfo.getSecond(), wrappedModifier, false);
        return Collections.singletonList(result);
      }
    }
    else if (resolved instanceof PyFunction) {
      final PyFunction function = (PyFunction)resolved;
      final TypeEvalContext context = resolveContext.getTypeEvalContext();

      if (function.getProperty() != null && isQualifiedByInstance(function, resolveResult.getQualifiers(), context)) {
        final PyType type = context.getReturnType(function);

        return type instanceof PyFunctionType
               ? Collections.singletonList(new ClarifiedResolveResult(resolveResult, ((PyFunctionType)type).getCallable(), null, false))
               : Collections.emptyList();
      }
    }

    final boolean isConstructor = PyUtil.isInitOrNewMethod(resolved) && call.getReceiver((PyCallable)resolved) == null;
    return resolved != null
           ? Collections.singletonList(new ClarifiedResolveResult(resolveResult, resolved, null, isConstructor))
           : Collections.emptyList();
  }

  @Nullable
  public static PyCallableType markResolveResult(@NotNull ClarifiedResolveResult resolveResult,
                                                 @NotNull TypeEvalContext context,
                                                 int implicitOffset) {
    final PsiElement clarifiedResolved = resolveResult.myClarifiedResolved;
    if (!(clarifiedResolved instanceof PyTypedElement)) return null;

    final PyCallableType callableType = PyUtil.as(context.getType((PyTypedElement)clarifiedResolved), PyCallableType.class);
    if (callableType == null) return null;

    if (clarifiedResolved instanceof PyCallable) {
      final PyCallable callable = (PyCallable)clarifiedResolved;

      final PyFunction.Modifier originalModifier = callable instanceof PyFunction ? ((PyFunction)callable).getModifier() : null;
      final PyFunction.Modifier resolvedModifier = ObjectUtils.chooseNotNull(originalModifier, resolveResult.myWrappedModifier);

      final boolean isConstructorCall = resolveResult.myIsConstructor;
      final List<PyExpression> qualifiers = resolveResult.myOriginalResolveResult.getQualifiers();

      final boolean isByInstance = isConstructorCall
                                   || isQualifiedByInstance(callable, qualifiers, context)
                                   || callable instanceof PyBoundFunction;

      final PyExpression lastQualifier = ContainerUtil.getLastItem(qualifiers);
      final boolean isByClass = lastQualifier != null && isQualifiedByClass(callable, lastQualifier, context);

      final int resolvedImplicitOffset =
        implicitOffset + getImplicitArgumentCount(callable, resolvedModifier, isConstructorCall, isByInstance, isByClass);

      if (callableType.getModifier() == resolvedModifier && callableType.getImplicitOffset() == resolvedImplicitOffset) {
        return callableType;
      }
      return new PyCallableTypeImpl(
        callableType.getParameters(context),
        callableType.getReturnType(context),
        callable,
        resolvedModifier,
        Math.max(0, resolvedImplicitOffset)); // wrong source can trigger strange behaviour
    }

    return new PyCallableTypeImpl(callableType.getParameters(context),
                                  callableType.getReturnType(context),
                                  null,
                                  null,
                                  implicitOffset);
  }

  /**
   * Calls the {@link #getImplicitArgumentCount(PyCallable, PyFunction.Modifier, boolean, boolean, boolean)} full version}
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
    if (modifier == PyFunction.Modifier.STATICMETHOD) {
      if (isByInstance && implicit_offset > 0) implicit_offset -= 1; // might have marked it as implicit 'self'
    }
    else if (modifier == PyFunction.Modifier.CLASSMETHOD) {
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
      if (qualifier != null && isQualifiedByInstance(resolved, qualifier, context)) {
        return true;
      }
    }
    return false;
  }

  private static boolean isQualifiedByInstance(@Nullable PyCallable resolved,
                                               @NotNull PyExpression qualifier,
                                               @NotNull TypeEvalContext context) {
    if (isQualifiedByClass(resolved, qualifier, context)) {
      return false;
    }
    final PyType qualifierType = context.getType(qualifier);
    if (qualifierType != null) {
      // TODO: handle UnionType
      if (qualifierType instanceof PyModuleType) return false; // qualified by module, not instance.
    }
    return true; // NOTE. best guess: unknown qualifier is more probably an instance.
  }

  private static boolean isQualifiedByClass(@Nullable PyCallable resolved,
                                            @NotNull PyExpression qualifier,
                                            @NotNull TypeEvalContext context) {
    final PyType qualifierType = context.getType(qualifier);

    if (qualifierType instanceof PyClassType) {
      final PyClassType qualifierClassType = (PyClassType)qualifierType;
      return qualifierClassType.isDefinition() && belongsToSpecifiedClassHierarchy(resolved, qualifierClassType.getPyClass(), context);
    }
    else if (qualifierType instanceof PyClassLikeType) {
      return ((PyClassLikeType)qualifierType).isDefinition(); // Any definition means callable is classmethod
    }
    else if (qualifierType instanceof PyUnionType) {
      final Collection<PyType> members = ((PyUnionType)qualifierType).getMembers();

      if (members.stream().allMatch(PyClassType.class::isInstance)) {
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
   * This method should not be called directly as it does not have a guard against recursion.
   */
  static PyType getCallType(@NotNull PyCallExpression call,
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
      final PyResolveContext resolveContext = PyResolveContext.defaultContext().withTypeEvalContext(context);

      final List<QualifiedRatedResolveResult> resolveResults = multiResolveCallee(callee, resolveContext);
      final Stream<QualifiedRatedResolveResult> overloadsOtherwiseImplementations =
        forEveryScopeTakeOverloadsOtherwiseImplementations(resolveResults, RatedResolveResult::getElement, context);

      final List<PyType> members = StreamEx
        .of(PyUtil.filterTopPriorityResults(overloadsOtherwiseImplementations.collect(Collectors.toList())))
        .map(ResolveResult::getElement)
        .nonNull()
        .peek(element -> PyUtil.verboseOnly(() -> PyPsiUtils.assertValid(element)))
        .map(element -> getCallTargetReturnType(call, element, context))
        .nonNull()
        .<PyType>map(Ref::get)
        .toList();

      if (!members.isEmpty()) {
        return PyUnionType.union(members);
      }
    }
    if (callee != null) {
      final PyType type = context.getType(callee);
      if (type instanceof PyCallableType) {
        final PyCallableType callableType = (PyCallableType)type;
        return callableType.getCallType(context, call);
      }
      if (type instanceof PyUnionType) {
        return getCallResultTypeFromUnion(call, context, (PyUnionType)type);
      }
    }
    return null;
  }

  /**
   * @return type that union will return if you call it
   */
  @Nullable
  private static PyType getCallResultTypeFromUnion(@NotNull final PyCallSiteExpression call,
                                                   @NotNull final TypeEvalContext context,
                                                   @NotNull final PyUnionType type) {
    final Collection<PyType> callResultTypes = new HashSet<>();

    for (final PyType memberType : type.getMembers()) {
      final Boolean callable = PyTypeChecker.isCallable(memberType);
      if (!((callable != null && callable && memberType instanceof PyCallableType))) {
        continue;
      }
      final PyCallableType callableMemberType = (PyCallableType)memberType;

      if (!callableMemberType.isCallable()) {
        continue;
      }
      final PyType callResultType = callableMemberType.getCallType(context, call);
      if (callResultType != null) {
        callResultTypes.add(callResultType);
      }
    }

    return PyUnionType.union(callResultTypes);
  }

  @Nullable
  private static Ref<? extends PyType> getCallTargetReturnType(@NotNull PyCallExpression call, @NotNull PsiElement target,
                                                               @NotNull TypeEvalContext context) {
    final Ref<PyType> providedOverridingType = PyReferenceExpressionImpl.getReferenceTypeFromOverridingProviders(target, context, call);
    if (providedOverridingType != null && providedOverridingType.get() instanceof PyCallableType) {
      return Ref.create(((PyCallableType)providedOverridingType.get()).getCallType(context, call));
    }

    PyClass cls = null;
    PyFunction init = null;
    if (target instanceof PyClass) {
      cls = (PyClass)target;
      init = cls.findInitOrNew(true, context);
    }
    else if (target instanceof PyFunction) {
      final PyFunction f = (PyFunction)target;
      if (PyUtil.isInitOrNewMethod(f)) {
        init = f;
        cls = f.getContainingClass();
      }
    }
    if (init != null) {
      final PyType t = init.getCallType(context, call);
      if (cls != null && cls != init.getContainingClass()) {
        if (t instanceof PyTupleType) {
          final PyTupleType tupleType = (PyTupleType)t;
          final PyTupleType newTupleType = new PyTupleType(cls, tupleType.getElementTypes(), tupleType.isHomogeneous());

          return Ref.create(newTupleType);
        }

        if (t instanceof PyCollectionType) {
          final List<PyType> elementTypes = ((PyCollectionType)t).getElementTypes();
          return Ref.create(new PyCollectionTypeImpl(cls, false, elementTypes));
        }

        return Ref.create(new PyClassTypeImpl(cls, false));
      }
      if (t != null) {
        return Ref.create(t);
      }
      if (cls != null) {
        final PyFunction newMethod = cls.findMethodByName(PyNames.NEW, true, context);
        if (newMethod != null && !PyBuiltinCache.getInstance(call).isBuiltin(newMethod)) {
          return Ref.create(PyUnionType.createWeakType(new PyClassTypeImpl(cls, false)));
        }
      }
    }
    if (cls != null) {
      return Ref.create(new PyClassTypeImpl(cls, false));
    }
    final Ref<PyType> providedType = PyReferenceExpressionImpl.getReferenceTypeFromProviders(target, context, call);
    if (providedType != null && providedType.get() instanceof PyCallableType) {
      return Ref.create(((PyCallableType)providedType.get()).getCallType(context, call));
    }
    final Ref<PyType> propertyCallType = getPropertyCallType(call, target, context);
    if (propertyCallType != null) {
      return propertyCallType;
    }
    if (target instanceof PyCallable) {
      final PyCallable callable = (PyCallable)target;
      return Ref.create(callable.getCallType(context, call));
    }
    return null;
  }

  @Nullable
  private static Ref<PyType> getPropertyCallType(@NotNull PyCallExpression call,
                                                 @NotNull PsiElement target,
                                                 @NotNull TypeEvalContext context) {
    if (target instanceof PyCallable && target instanceof PyPossibleClassMember) {
      final PyClass containingClass = ((PyPossibleClassMember)target).getContainingClass();
      if (containingClass != null) {
        final PyCallable callable = (PyCallable)target;
        final Property property = containingClass.findPropertyByCallable(callable);
        if (property != null) {
          final PyType propertyType = property.getType(call.getReceiver(callable), context);
          if (propertyType instanceof PyCallableType) {
            return Ref.create(((PyCallableType)propertyType).getCallType(context, call));
          }
        }
      }
    }
    return null;
  }

  @NotNull
  private static Maybe<PyType> getSuperCallType(@NotNull PyCallExpression call, @NotNull TypeEvalContext context) {
    final PyExpression callee = call.getCallee();
    if (callee instanceof PyReferenceExpression) {
      PsiElement must_be_super_init = ((PyReferenceExpression)callee).getReference().resolve();
      if (must_be_super_init instanceof PyFunction) {
        PyClass must_be_super = ((PyFunction)must_be_super_init).getContainingClass();
        if (must_be_super == PyBuiltinCache.getInstance(call).getClass(PyNames.SUPER)) {
          final PyArgumentList argumentList = call.getArgumentList();
          if (argumentList != null) {
            final PyClass containingClass = PsiTreeUtil.getParentOfType(call, PyClass.class);
            PyExpression[] args = argumentList.getArguments();
            if (containingClass != null && args.length > 1) {
              PyExpression first_arg = args[0];
              if (first_arg instanceof PyReferenceExpression) {
                final PyReferenceExpression firstArgRef = (PyReferenceExpression)first_arg;
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
                if (possible_class instanceof PyClass && ((PyClass)possible_class).isNewStyleClass(context)) {
                  final PyClass first_class = (PyClass)possible_class;
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

  @NotNull
  public static List<PyCallExpression.PyArgumentsMapping> multiMapArguments(@NotNull PyCallExpression callExpression,
                                                                            @NotNull PyResolveContext resolveContext,
                                                                            int implicitOffset) {
    final PyArgumentList argumentList = callExpression.getArgumentList();
    if (argumentList == null) {
      return Collections.emptyList();
    }

    final TypeEvalContext context = resolveContext.getTypeEvalContext();
    return ContainerUtil.map(callExpression.multiResolveCallee(resolveContext, implicitOffset),
                             callableType -> mapArguments(callExpression, argumentList, callableType, context));
  }

  @NotNull
  public static PyCallExpression.PyArgumentsMapping mapArguments(@NotNull PyCallExpression callExpression,
                                                                 @NotNull PyCallableType callableType,
                                                                 @NotNull TypeEvalContext context) {
    final PyArgumentList argumentList = callExpression.getArgumentList();
    if (argumentList == null) {
      return PyCallExpression.PyArgumentsMapping.empty(callExpression);
    }

    return mapArguments(callExpression, argumentList, callableType, context);
  }

  @NotNull
  private static PyCallExpression.PyArgumentsMapping mapArguments(@NotNull PyCallExpression callExpression,
                                                                  @NotNull PyArgumentList argumentList,
                                                                  @NotNull PyCallableType callableType,
                                                                  @NotNull TypeEvalContext context) {
    final List<PyCallableParameter> parameters = callableType.getParameters(context);
    if (parameters == null) return PyCallExpression.PyArgumentsMapping.empty(callExpression);

    final int safeImplicitOffset = Math.min(callableType.getImplicitOffset(), parameters.size());
    final List<PyCallableParameter> explicitParameters = parameters.subList(safeImplicitOffset, parameters.size());
    final List<PyCallableParameter> implicitParameters = parameters.subList(0, safeImplicitOffset);
    final List<PyExpression> arguments = Arrays.asList(argumentList.getArguments());
    final ArgumentMappingResults mappingResults = analyzeArguments(arguments, explicitParameters);

    return new PyCallExpression.PyArgumentsMapping(callExpression,
                                                   callableType,
                                                   implicitParameters,
                                                   mappingResults.getMappedParameters(),
                                                   mappingResults.getUnmappedParameters(),
                                                   mappingResults.getUnmappedArguments(),
                                                   mappingResults.getParametersMappedToVariadicPositionalArguments(),
                                                   mappingResults.getParametersMappedToVariadicKeywordArguments(),
                                                   mappingResults.getMappedTupleParameters());
  }

  @NotNull
  public static List<PyCallExpression.PyArgumentsMapping> mapArguments(@NotNull PyCallSiteExpression callSite,
                                                                       @NotNull PyResolveContext resolveContext) {
    final List<PyCallExpression.PyArgumentsMapping> results = new ArrayList<>();
    for (Pair<PyCallable, PyCallableType> callableAndType : multiResolveCalleeFunction(callSite, resolveContext)) {
      results.add(mapArguments(callSite, callableAndType.second, callableAndType.first, resolveContext));
    }
    return results;
  }

  @NotNull
  private static List<Pair<PyCallable, PyCallableType>> multiResolveCalleeFunction(@NotNull PyCallSiteExpression callSite,
                                                                                   @NotNull PyResolveContext resolveContext) {
    if (callSite instanceof PyCallExpression) {
      final List<PyCallableType> callableTypes = ((PyCallExpression)callSite).multiResolveCallee(resolveContext);

      return StreamEx
        .of(callableTypes)
        .map(callableType -> Pair.create(callableType.getCallable(), callableType))
        .toList();
    }
    else {
      final List<Pair<PyCallable, PyCallableType>> results = new ArrayList<>();

      for (PsiElement result : PyUtil.multiResolveTopPriority(callSite, resolveContext)) {
        if (result instanceof PyTypedElement) {
          final PyType resultType = resolveContext.getTypeEvalContext().getType((PyTypedElement)result);
          if (resultType instanceof PyCallableType) {
            final PyCallable callable = PyUtil.as(result, PyCallable.class);
            results.add(Pair.create(callable, (PyCallableType)resultType));
            continue;
          }
        }
        return Collections.emptyList();
      }

      return results;
    }
  }

  @NotNull
  public static PyCallExpression.PyArgumentsMapping mapArguments(@NotNull PyCallSiteExpression callSite,
                                                                 @NotNull PyCallable callable,
                                                                 @NotNull TypeEvalContext context) {
    final PyCallableType callableType = PyUtil.as(context.getType(callable), PyCallableType.class);
    if (callableType == null) return PyCallExpression.PyArgumentsMapping.empty(callSite);

    return mapArguments(callSite, callableType, callable, PyResolveContext.defaultContext().withTypeEvalContext(context));
  }

  @NotNull
  private static PyCallExpression.PyArgumentsMapping mapArguments(@NotNull PyCallSiteExpression callSite,
                                                                  @NotNull PyCallableType callableType,
                                                                  @Nullable PyCallable callable,
                                                                  @NotNull PyResolveContext resolveContext) {
    final TypeEvalContext context = resolveContext.getTypeEvalContext();
    final List<PyCallableParameter> parameters = callableType.getParameters(context);
    if (parameters == null) return PyCallExpression.PyArgumentsMapping.empty(callSite);

    final List<PyExpression> arguments = callSite.getArguments(callable);
    final List<PyCallableParameter> explicitParameters = filterExplicitParameters(parameters, callable, callSite, resolveContext);
    final List<PyCallableParameter> implicitParameters = parameters.subList(0, parameters.size() - explicitParameters.size());

    final ArgumentMappingResults mappingResults = analyzeArguments(arguments, explicitParameters);

    return new PyCallExpression.PyArgumentsMapping(callSite,
                                                   callableType,
                                                   implicitParameters,
                                                   mappingResults.getMappedParameters(),
                                                   mappingResults.getUnmappedParameters(),
                                                   mappingResults.getUnmappedArguments(),
                                                   mappingResults.getParametersMappedToVariadicPositionalArguments(),
                                                   mappingResults.getParametersMappedToVariadicKeywordArguments(),
                                                   mappingResults.getMappedTupleParameters());
  }

  @NotNull
  public static List<PyExpression> getArgumentsMappedToPositionalContainer(@NotNull Map<PyExpression, PyCallableParameter> mapping) {
    return StreamEx.ofKeys(mapping, PyCallableParameter::isPositionalContainer).toList();
  }

  @NotNull
  public static List<PyExpression> getArgumentsMappedToKeywordContainer(@NotNull Map<PyExpression, PyCallableParameter> mapping) {
    return StreamEx.ofKeys(mapping, PyCallableParameter::isKeywordContainer).toList();
  }

  @NotNull
  public static Map<PyExpression, PyCallableParameter> getRegularMappedParameters(@NotNull Map<PyExpression, PyCallableParameter> mapping) {
    final Map<PyExpression, PyCallableParameter> result = new LinkedHashMap<>();
    for (Map.Entry<PyExpression, PyCallableParameter> entry : mapping.entrySet()) {
      final PyExpression argument = entry.getKey();
      final PyCallableParameter parameter = entry.getValue();
      if (!parameter.isPositionalContainer() && !parameter.isKeywordContainer()) {
        result.put(argument, parameter);
      }
    }
    return result;
  }

  @Nullable
  public static PyCallableParameter getMappedPositionalContainer(@NotNull Map<PyExpression, PyCallableParameter> mapping) {
    return mapping.values().stream().filter(p -> p.isPositionalContainer()).findFirst().orElse(null);
  }

  @Nullable
  public static PyCallableParameter getMappedKeywordContainer(@NotNull Map<PyExpression, PyCallableParameter> mapping) {
    return mapping.values().stream().filter(p -> p.isKeywordContainer()).findFirst().orElse(null);
  }

  @NotNull
  public static Collection<? extends PsiElement> resolveConstructors(@NotNull PyClass cls,
                                                                     @NotNull PyExpression location,
                                                                     @NotNull TypeEvalContext context,
                                                                     boolean inherited) {
    final List<PsiElement> result = new ArrayList<>();
    result.addAll(preferInitOverNew(cls.multiFindInitOrNew(inherited, context)));
    result.addAll(resolveMetaclassDunderCall(cls, location, context));
    return result;
  }

  @NotNull
  private static Collection<? extends PyFunction> preferInitOverNew(@NotNull List<PyFunction> initAndNew) {
    final MultiMap<String, PyFunction> functions = ContainerUtil.groupBy(initAndNew, PyFunction::getName);
    return functions.containsKey(PyNames.INIT) ? functions.get(PyNames.INIT) : functions.values();
  }

  @NotNull
  private static List<PsiElement> resolveMetaclassDunderCall(@NotNull PyClass cls,
                                                             @Nullable PyExpression location,
                                                             @NotNull TypeEvalContext context) {
    final PyClassLikeType metaClassType = cls.getMetaClassType(true, context);
    if (metaClassType == null) return Collections.emptyList();

    final PyClassType typeType = PyBuiltinCache.getInstance(cls).getTypeType();
    if (metaClassType == typeType) return Collections.emptyList();

    final PyResolveContext resolveContext = PyResolveContext.defaultContext().withTypeEvalContext(context);
    final List<? extends RatedResolveResult> results =
      PyUtil.filterTopPriorityResults(resolveDunderCall(metaClassType, location, resolveContext));
    if (results.isEmpty()) return Collections.emptyList();

    final Set<PsiElement> typeDunderCall =
      ContainerUtil.map2Set(resolveDunderCall(typeType, null, resolveContext), ResolveResult::getElement);

    return StreamEx
      .of(results)
      .map(ResolveResult::getElement)
      .remove(it -> typeDunderCall.contains(it) || isSelfArgsKwargsCallable(it, context))
      .toList();
  }

  @NotNull
  private static List<? extends RatedResolveResult> resolveDunderCall(@Nullable PyClassLikeType type,
                                                                      @Nullable PyExpression location,
                                                                      @NotNull PyResolveContext resolveContext) {
    if (type == null) return Collections.emptyList();
    return ObjectUtils.notNull(type.resolveMember(PyNames.CALL, location, AccessDirection.READ, resolveContext), Collections.emptyList());
  }

  private static boolean isSelfArgsKwargsCallable(@Nullable PsiElement element, @NotNull TypeEvalContext context) {
    if (element instanceof PyCallable) {
      final List<PyCallableParameter> parameters = ((PyCallable)element).getParameters(context);
      return parameters.size() == 3 &&
             parameters.get(0).isSelf() &&
             parameters.get(1).isPositionalContainer() &&
             parameters.get(2).isKeywordContainer();
    }

    return false;
  }

  @NotNull
  private static ArgumentMappingResults analyzeArguments(@NotNull List<PyExpression> arguments,
                                                         @NotNull List<PyCallableParameter> parameters) {
    boolean positionalOnlyMode = ContainerUtil.exists(parameters, p -> p.getParameter() instanceof PySlashParameter);
    boolean seenSingleStar = false;
    boolean mappedVariadicArgumentsToParameters = false;
    final Map<PyExpression, PyCallableParameter> mappedParameters = new LinkedHashMap<>();
    final List<PyCallableParameter> unmappedParameters = new ArrayList<>();
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

    return new ArgumentMappingResults(mappedParameters, unmappedParameters, unmappedArguments,
                                      parametersMappedToVariadicPositionalArguments, parametersMappedToVariadicKeywordArguments,
                                      tupleMappedParameters);
  }

  @NotNull
  public static <E> Stream<E> forEveryScopeTakeOverloadsOtherwiseImplementations(@NotNull Collection<E> elements,
                                                                                 @NotNull Function<? super E, PsiElement> mapper,
                                                                                 @NotNull TypeEvalContext context) {
    if (!containsOverloadsAndImplementations(elements, mapper, context)) {
      return elements.stream();
    }

    return StreamEx
      .of(elements)
      .groupingBy(element -> Optional.ofNullable(ScopeUtil.getScopeOwner(mapper.apply(element))))
      .values()
      .stream()
      .flatMap(oneScopeElements -> takeOverloadsOtherwiseImplementations(oneScopeElements, mapper, context));
  }

  private static <E> boolean containsOverloadsAndImplementations(@NotNull Collection<E> elements,
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
  private static <E> Stream<E> takeOverloadsOtherwiseImplementations(@NotNull List<E> elements,
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
          return mapped != null && PyiUtil.isOverload(mapped, context);
        }
      );
  }

  public static class ArgumentMappingResults {
    @NotNull private final Map<PyExpression, PyCallableParameter> myMappedParameters;
    @NotNull private final List<PyCallableParameter> myUnmappedParameters;
    @NotNull private final List<PyExpression> myUnmappedArguments;
    @NotNull private final List<PyCallableParameter> myParametersMappedToVariadicPositionalArguments;
    @NotNull private final List<PyCallableParameter> myParametersMappedToVariadicKeywordArguments;
    @NotNull private final Map<PyExpression, PyCallableParameter> myMappedTupleParameters;

    public ArgumentMappingResults(@NotNull Map<PyExpression, PyCallableParameter> mappedParameters,
                                  @NotNull List<PyCallableParameter> unmappedParameters,
                                  @NotNull List<PyExpression> unmappedArguments,
                                  @NotNull List<PyCallableParameter> parametersMappedToVariadicPositionalArguments,
                                  @NotNull List<PyCallableParameter> parametersMappedToVariadicKeywordArguments,
                                  @NotNull Map<PyExpression, PyCallableParameter> mappedTupleParameters) {
      myMappedParameters = mappedParameters;
      myUnmappedParameters = unmappedParameters;
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
    if (argument instanceof PySequenceExpression) {
      final PySequenceExpression sequenceExpr = (PySequenceExpression)argument;
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
          if (expr instanceof PySequenceExpression) {
            final PySequenceExpression sequenceExpr = (PySequenceExpression)expr;
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
    if (callSite instanceof PyCallExpression) {
      final PyCallExpression callExpr = (PyCallExpression)callSite;
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

  public static class ClarifiedResolveResult {

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
