// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.psi.impl;

import com.intellij.codeInsight.controlflow.ConditionalInstruction;
import com.intellij.codeInsight.controlflow.ControlFlowUtil;
import com.intellij.codeInsight.controlflow.Instruction;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiInvalidElementAccessException;
import com.intellij.psi.PsiPolyVariantReference;
import com.intellij.psi.ResolveResult;
import com.intellij.psi.StubBasedPsiElement;
import com.intellij.psi.impl.source.resolve.FileContextUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.QualifiedName;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.PythonRuntimeService;
import com.jetbrains.python.codeInsight.controlflow.ControlFlowCache;
import com.jetbrains.python.codeInsight.controlflow.PyTypeAssertionEvaluator;
import com.jetbrains.python.codeInsight.controlflow.ReadWriteInstruction;
import com.jetbrains.python.codeInsight.controlflow.ScopeOwner;
import com.jetbrains.python.codeInsight.dataflow.scope.ScopeUtil;
import com.jetbrains.python.inspections.PyInspectionMessages.ProblemMessage;
import com.jetbrains.python.psi.AccessDirection;
import com.jetbrains.python.psi.Property;
import com.jetbrains.python.psi.PyAugAssignmentStatement;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyDecorator;
import com.jetbrains.python.psi.PyDecoratorList;
import com.jetbrains.python.psi.PyDocStringOwner;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.psi.PyElementVisitor;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyExpressionCodeFragment;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyFromImportStatement;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyImportElement;
import com.jetbrains.python.psi.PyNamedParameter;
import com.jetbrains.python.psi.PyQualifiedExpression;
import com.jetbrains.python.psi.PyReferenceExpression;
import com.jetbrains.python.psi.PyTargetExpression;
import com.jetbrains.python.psi.PyTypedElement;
import com.jetbrains.python.psi.PyUtil;
import com.jetbrains.python.psi.impl.references.PyImportReference;
import com.jetbrains.python.psi.impl.references.PyQualifiedReference;
import com.jetbrains.python.psi.impl.references.PyReferenceImpl;
import com.jetbrains.python.psi.resolve.ImplicitResolveResult;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.resolve.QualifiedNameFinder;
import com.jetbrains.python.psi.resolve.QualifiedRatedResolveResult;
import com.jetbrains.python.psi.resolve.QualifiedResolveResult;
import com.jetbrains.python.psi.resolve.RatedResolveResult;
import com.jetbrains.python.psi.types.PyAnyType;
import com.jetbrains.python.psi.types.PyCallableType;
import com.jetbrains.python.psi.types.PyClassLikeType;
import com.jetbrains.python.psi.types.PyClassType;
import com.jetbrains.python.psi.types.PyCompositeType;
import com.jetbrains.python.psi.types.PyDescriptorTypeUtil;
import com.jetbrains.python.psi.types.PyImportedModuleType;
import com.jetbrains.python.psi.types.PyInstantiableType;
import com.jetbrains.python.psi.types.PyIntersectionType;
import com.jetbrains.python.psi.types.PyLiteralType;
import com.jetbrains.python.psi.types.PyModuleType;
import com.jetbrains.python.psi.types.PyNarrowedType;
import com.jetbrains.python.psi.types.PyOverloadType;
import com.jetbrains.python.psi.types.PySyntheticCallHelper;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.PyTypeParameterType;
import com.jetbrains.python.psi.types.PyTypeUtil;
import com.jetbrains.python.psi.types.PyTypeUtilKt;
import com.jetbrains.python.psi.types.PyTypeVarType;
import com.jetbrains.python.psi.types.PyUnionType;
import com.jetbrains.python.psi.types.PyUnsafeUnionType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import com.jetbrains.python.psi.types.TypeEvalContextImpl;
import com.jetbrains.python.codeInsight.typing.PyTypingTypeProvider;
import com.jetbrains.python.pyi.PyiUtil;
import com.jetbrains.python.refactoring.PyDefUseUtil;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.function.Predicate;

import static com.jetbrains.python.psi.types.PyNoneTypeKt.isNoneType;
import static com.jetbrains.python.psi.types.PyTypeUtilKt.isUnknown;

/**
 * Implements reference expression PSI.
 */
public class PyReferenceExpressionImpl extends PyElementImpl implements PyReferenceExpression {

  // PY-89956: guards against re-entrant def-use chain warming (see warmEarlierDefinitionTypes).
  private static final ThreadLocal<Boolean> ourWarmingDefUseChain = ThreadLocal.withInitial(() -> Boolean.FALSE);
  // Minimum number of earlier same-name definitions before we pre-warm their types; short chains are left untouched.
  private static final int WARM_DEF_USE_THRESHOLD = 64;

  private record ControlFlowTypeResult(@Nullable PyType type, boolean foundPrefixCall) {
    private ControlFlowTypeResult {
      PyAnyType.validate(type);
    }
  }

  private volatile @Nullable QualifiedName myQualifiedName = null;

  public PyReferenceExpressionImpl(@NotNull ASTNode astNode) {
    super(astNode);
  }

  @Override
  public @NotNull PsiPolyVariantReference getReference() {
    assert !(this instanceof StubBasedPsiElement);
    final TypeEvalContext context = TypeEvalContext.codeAnalysis(getProject(), getContainingFile());
    return getReference(PyResolveContext.defaultContext(context));
  }

  @Override
  public @NotNull PsiPolyVariantReference getReference(@NotNull PyResolveContext context) {
    // Handle import reference
    final PsiElement importParent = getImportParent();
    if (importParent != null) {
      return PyImportReference.forElement(this, importParent, context);
    }

    final PyExpression qualifier = getQualifier();

    // Return special reference
    PsiPolyVariantReference ref = PythonRuntimeService.getInstance().getPydevConsoleReference(this, context);
    if (ref != null) {
      return ref;
    }

    if (qualifier != null) {
      return new PyQualifiedReference(this, context);
    }

    return new PyReferenceImpl(this, context);
  }

  private @Nullable PsiElement getImportParent() {
    return PsiTreeUtil.getParentOfType(this, PyImportElement.class, PyFromImportStatement.class);
  }

  @Override
  protected void acceptPyVisitor(PyElementVisitor pyVisitor) {
    pyVisitor.visitPyReferenceExpression(this);
  }

  @Override
  public @Nullable String getName() {
    return PyReferenceExpression.super.getName();
  }

  @Override
  public @NotNull QualifiedResolveResult followAssignmentsChain(@NotNull PyResolveContext resolveContext) {
    final List<QualifiedRatedResolveResult> resolveResults = multiFollowAssignmentsChain(resolveContext);

    return resolveResults
      .stream()
      .filter(result -> !result.isImplicit())
      .findFirst()
      .map(QualifiedResolveResult.class::cast)
      .orElseGet(
        () -> {
          final QualifiedResolveResult first = ContainerUtil.getFirstItem(resolveResults);
          return first == null ? QualifiedResolveResult.EMPTY : first;
        }
      );
  }

  @Override
  public @NotNull List<QualifiedRatedResolveResult> multiFollowAssignmentsChain(@NotNull PyResolveContext resolveContext,
                                                                                @NotNull Predicate<? super PyTargetExpression> follow) {
    final List<QualifiedRatedResolveResult> result = new ArrayList<>();
    final Queue<MultiFollowQueueNode> queue = new LinkedList<>();
    final Set<PyReferenceExpression> visited = new HashSet<>();

    queue.add(MultiFollowQueueNode.create(null, this));
    visited.add(this);
    final TypeEvalContext context = resolveContext.getTypeEvalContext();

    while (!queue.isEmpty()) {
      final MultiFollowQueueNode node = queue.remove();

      for (ResolveResult resolveResult : node.myReferenceExpression.getReference(resolveContext).multiResolve(false)) {
        final PsiElement element = resolveResult.getElement();
        if (element instanceof PyTargetExpression target && follow.test(target)) {

          final List<PsiElement> assignedFromElements = context.maySwitchToAST(target)
                                                        ? Collections.singletonList(target.findAssignedValue())
                                                        : target.multiResolveAssignedValue(resolveContext);

          for (PsiElement assignedFrom : assignedFromElements) {
            if (assignedFrom instanceof PyReferenceExpression assignedReference) {

              if (!visited.add(assignedReference)) continue;

              queue.add(MultiFollowQueueNode.create(node, assignedReference));
            }
            else if (assignedFrom != null) {
              result.add(
                new QualifiedRatedResolveResult(
                  assignedFrom,
                  node.myQualifiers,
                  resolveResult instanceof RatedResolveResult ? ((RatedResolveResult)resolveResult).getRate() : 0,
                  resolveResult instanceof ImplicitResolveResult
                )
              );
            }
          }
        }
        else if (element != null && resolveResult.isValidResult()) {
          result.add(
            new QualifiedRatedResolveResult(
              element,
              node.myQualifiers,
              resolveResult instanceof RatedResolveResult ? ((RatedResolveResult)resolveResult).getRate() : 0,
              resolveResult instanceof ImplicitResolveResult
            )
          );
        }
      }
    }

    return result;
  }

  @Override
  public @Nullable QualifiedName asQualifiedName() {
    if (myQualifiedName == null) {
      myQualifiedName = PyPsiUtils.asQualifiedName(this);
    }
    return myQualifiedName;
  }

  @Override
  public String toString() {
    return "PyReferenceExpression: " + getReferencedName();
  }

  @Override
  public @Nullable PyType getType(@NotNull TypeEvalContext context, @NotNull TypeEvalContext.Key key) {
    final PyType providedType = getTypeFromProviders(context);
    if (!isUnknown(providedType)) {
      return providedType;
    }

    if (isQualified() && getImportParent() == null) {
      return getQualifiedReferenceType(this, context, null);
    }

    return PyTypeUtil.derefOrUnknown(getTypeFromTargets(context));
  }

  public static @Nullable PyType getQualifiedReferenceType(@NotNull PyReferenceExpression refExpr,
                                                           @NotNull TypeEvalContext context,
                                                           @Nullable List<ProblemMessage> errors) {
    final PyExpression qualifier = Objects.requireNonNull(refExpr.getQualifier());

    final String attrName = refExpr.getName();
    if (attrName == null) return PyAnyType.getUnknown();

    PyType qualifierType = PyLiteralType.getLiteralType(qualifier, context);
    if (qualifierType == null) {
      qualifierType = context.getType(qualifier);
    }

    final Ref<PyType> typeOfProperty = getTypeOfProperty(refExpr, qualifierType, attrName, context);
    if (typeOfProperty != null) {
      return typeOfProperty.get();
    }

    // This code performs a backwards traversal through the Control Flow Graph to analyze assignments.
    // It searches for WRITE instructions involving `qualifier.this_name` with the following behavior:
    //
    // 1. If WRITE instructions are found on all possible execution paths:
    //    - Returns a union type combining the types from all getType() calls on those instructions
    //
    // 2. If a WRITE instruction involving just the `qualifier` is found on any path
    //    (via PyTargetExpression or PyNamedParameter):
    //    - The analysis stops and returns null, ignoring any other paths
    //
    // 3. If a CallInstruction involving just the `qualifier` as an argument is found on any path:
    //    - We assume the call *might* have affeted the tupe of `this_name`, and return UnsafeUnion[result_from_cfg, result_from_targets]
    //
    // (see PyDefUseUtil.getLatestDefs)
    //
    // Note on getType() behavior for PyTargetExpression:
    // - First queries PyTypeProviders (including PyTypingTypeProvider)
    // - PyTypingTypeProvider checks if qualifier's class has a type annotation for 'this_name'
    //   and returns that annotated type if found
    // - If no providers return a type, falls back to returning the type of the assigned value

    final ControlFlowTypeResult controlFlowResult = doGetQualifiedReferenceTypeByControlFlow(refExpr, context);
    final PyType typeByControlFlow = controlFlowResult.type();
    if (!isUnknown(typeByControlFlow)) {
      if (controlFlowResult.foundPrefixCall()) {
        // A call with prefix as receiver/argument may or may not mutate it, so return UnsafeUnion of narrowed and declared types (PY-88265)
        PyType declaredType = getTypeOfMember(qualifierType, null, attrName, refExpr, PyResolveContext.noProperties(context), errors);
        if (isNoneType(declaredType)) {
          declaredType = PyAnyType.getUnknown();
        }
        return PyUnsafeUnionType.unsafeUnion(typeByControlFlow, declaredType);
      }
      return typeByControlFlow;
    }

    return getTypeOfMember(qualifierType, null, attrName, refExpr, PyResolveContext.noProperties(context), errors);
  }

  private @Nullable Ref<PyType> getTypeFromTargets(@NotNull TypeEvalContext context) {
    final PyResolveContext resolveContext = PyResolveContext.defaultContext(context);

    final PsiFile realFile = FileContextUtil.getContextFile(this);
    if (!(getContainingFile() instanceof PyExpressionCodeFragment) || (realFile != null && context.maySwitchToAST(realFile))) {
      return getTypeFromTargets(PyUtil.multiResolveTopPriority(getReference(resolveContext)), context, this);
    }

    return null;
  }

  private static @Nullable Ref<PyType> getTypeFromTargets(@NotNull List<@NotNull PsiElement> targets,
                                                          @NotNull TypeEvalContext context,
                                                          @NotNull PyQualifiedExpression anchor) {
    final List<Ref<PyType>> members = new ArrayList<>();
    final var overloadMembers = new ArrayList<>();
    for (PsiElement target : targets) {
      if (target == anchor) {
        continue;
      }

      if (overloadMembers.contains(target)) {
        continue;
      }

      if (!target.isValid()) {
        throw new PsiInvalidElementAccessException(anchor);
      }

      var member = getTypeFromTarget(target, context, anchor);
      if (Ref.deref(member) instanceof PyOverloadType && target instanceof PyFunction function) {
        overloadMembers.addAll(PyiUtil.getOverloads(function, context));
      }
      members.add(member);
    }

    return members.stream().collect(PyTypeUtil.toUnionFromRef());
  }

  private static @NotNull ControlFlowTypeResult doGetQualifiedReferenceTypeByControlFlow(@NotNull PyReferenceExpression refExpr,
                                                                                         @NotNull TypeEvalContext context) {
    PyExpression qualifier = refExpr.getQualifier();
    if (context.allowDataFlow(refExpr) && qualifier != null) {
      PyExpression next = qualifier;
      while (next != null) {
        qualifier = next;
        next = qualifier instanceof PyQualifiedExpression ? ((PyQualifiedExpression)qualifier).getQualifier() : null;
      }
      final ScopeOwner scopeOwner = ScopeUtil.getScopeOwner(refExpr);
      final QualifiedName qname = refExpr.asQualifiedName();
      if (qname != null && scopeOwner != null) {
        return getTypeByControlFlow(qname.toString(), context, qualifier, scopeOwner);
      }
    }
    return new ControlFlowTypeResult(PyAnyType.getUnknown(), false);
  }

  private static @Nullable Ref<PyType> getTypeOfProperty(@NotNull PyReferenceExpression refExpr,
                                                         @Nullable PyType qualifierType,
                                                         @NotNull String name,
                                                         @NotNull TypeEvalContext context) {
    if (qualifierType instanceof PyClassType classType) {
      final PyClass pyClass = classType.getPyClass();

      // TODO PY-90645: This special-casing should be revisited and possibly removed once we handle data descriptors generically.
      // on a class object, a property defined on the metaclass is a data descriptor and takes
      // precedence over a member of the same name on the class itself. The class is an instance of its
      // metaclass, so the metaclass property's getter is invoked with the class object as the receiver.
      if (classType.isDefinition() && AccessDirection.of(refExpr) == AccessDirection.READ) {
        final Ref<PyType> metaClassProperty = getMetaclassPropertyTypeForClassAccess(refExpr, classType, name, context);
        if (metaClassProperty != null) {
          return metaClassProperty;
        }
      }

      final Property property = pyClass.findProperty(name, true, context);

      if (property != null) {
        if (classType.isDefinition()) {
          return Ref.create(PyBuiltinCache.getInstance(pyClass).getObjectType(PyNames.PROPERTY));
        }
        if (AccessDirection.of(refExpr) == AccessDirection.READ) {
          final PyType type = property.getType(refExpr.getQualifier(), context);
          if (type != null) {
            return Ref.create(type);
          }
        }
        return Ref.create();
      }
    }
    else if (qualifierType instanceof PyUnionType unionType) {
      for (PyType type : unionType.getMembers()) {
        final Ref<PyType> result = getTypeOfProperty(refExpr, type, name, context);
        if (result != null) {
          return result;
        }
      }
    }
    else if (qualifierType instanceof PyUnsafeUnionType unionType) {
      for (PyType type : unionType.getMembers()) {
        final Ref<PyType> result = getTypeOfProperty(refExpr, type, name, context);
        if (result != null) {
          return result;
        }
      }
    }
    else if (qualifierType instanceof PyTypeParameterType typeParameterType) {
      final PyType effectiveBound = typeParameterType.getEffectiveBound();
      if (effectiveBound != null && effectiveBound != qualifierType) {
        return getTypeOfProperty(refExpr, effectiveBound, name, context);
      }
    }

    return null;
  }

  private static @Nullable Ref<PyType> getMetaclassPropertyTypeForClassAccess(@NotNull PyReferenceExpression refExpr,
                                                                              @NotNull PyClassType classType,
                                                                              @NotNull String name,
                                                                              @NotNull TypeEvalContext context) {
    final PyClassLikeType metaClassType = classType.getMetaClassType(context, true);
    if (!(metaClassType instanceof PyClassType metaPyClassType)) {
      return null;
    }
    final Property metaProperty = metaPyClassType.getPyClass().findProperty(name, true, context);
    if (metaProperty == null) {
      return null;
    }
    final PyType type = metaProperty.getType(refExpr.getQualifier(), context);
    return Ref.create(type);
  }

  /**
   * Resolves `attrName` on `type` and binds the result to `selfType`,
   * or to the type it was resolved from when `selfType` is `null`.
   */
  private static @Nullable PyType getTypeOfMember(@Nullable PyType type,
                                                  @Nullable PyInstantiableType<?> selfType,
                                                  @NotNull String attrName,
                                                  @NotNull PyQualifiedExpression anchor,
                                                  @NotNull PyResolveContext resolveContext,
                                                  @Nullable List<ProblemMessage> errors) {
    if (type instanceof PyCompositeType compositeType) {
      StreamEx<@Nullable PyType> types = StreamEx.of(compositeType.getMembers())
        .map(it -> getTypeOfMember(it, selfType, attrName, anchor, resolveContext, errors));
      return switch (compositeType) {
        case PyIntersectionType ignored -> types.filter(t -> !isUnknown(t)).findFirst().orElse(PyAnyType.getUnknown());
        case PyUnsafeUnionType ignored -> PyUnsafeUnionType.unsafeUnion(types.toList());
        default -> PyUnionType.union(types.toList());
      };
    }

    // Return `str` for `__doc__` if a docstring is present; otherwise fall through to the regular `object.__doc__` attribute.
    if (PyNames.DOC.equals(attrName)) {
      PyDocStringOwner docStringOwner = switch (type) {
        case PyClassType classType -> classType.getPyClass();
        case PyModuleType moduleType -> moduleType.getModule();
        case PyCallableType callableType when callableType.getCallable() instanceof PyFunction function -> function;
        case null, default -> null;
      };
      if (docStringOwner != null && docStringOwner.getDocStringValue() != null) {
        return PyBuiltinCache.getInstance(docStringOwner).getStrType();
      }
    }

    if (type instanceof PyClassType classType) {
      return getTypeOfClassMember(classType, selfType == null ? classType : selfType, attrName, anchor, resolveContext, errors);
    }
    if (type instanceof PyTypeVarType typeVarType) {
      // Use type var bound/constraints for attribute resolution. Bind to type var itself.
      if (!typeVarType.getConstraints().isEmpty()) {
        return PyUnionType.union(
          ContainerUtil.map(typeVarType.getConstraints(),
                            it -> getTypeOfMember(it, typeVarType, attrName, anchor, resolveContext, errors))
        );
      }
      else if (typeVarType.getBound() != null) {
        return getTypeOfMember(typeVarType.getBound(), typeVarType, attrName, anchor, resolveContext, errors);
      }
      return PyAnyType.getUnknown();
    }
    if (PyTypeUtilKt.isAny(type)) {
      return PyAnyType.getAny();
    }
    var resolveResults = type.resolveMember(attrName, anchor, AccessDirection.READ, resolveContext);
    if (resolveResults != null) {
      List<PsiElement> resolvedElements = PyUtil.filterTopPriorityElements(resolveResults);
      Ref<PyType> result = getTypeFromTargets(resolvedElements, resolveContext.getTypeEvalContext(), anchor);
      if (result != null) {
        return result.get();
      }
    }
    return PyAnyType.getUnknown();
  }

  private static @Nullable PyType getTypeOfClassMember(@NotNull PyClassType classType,
                                                       @NotNull PyInstantiableType<?> selfType,
                                                       @NotNull String name,
                                                       @NotNull PyQualifiedExpression anchor,
                                                       @NotNull PyResolveContext resolveContext,
                                                       @Nullable List<ProblemMessage> errors) {
    TypeEvalContext context = resolveContext.getTypeEvalContext();

    List<? extends RatedResolveResult> resolveResults = classType.resolveMember(name, null, AccessDirection.READ, resolveContext);
    if (resolveResults == null || resolveResults.isEmpty()) {
      PyType nameArg = Optional.<PyType>ofNullable(PyLiteralType.stringLiteral(anchor, name)).orElse(PyAnyType.getUnknown());
      return PySyntheticCallHelper.getCallTypeByFunctionName(PyNames.GETATTR, classType, Collections.singletonList(nameArg), context);
    }

    List<PyType> providedTypes = StreamEx.of(resolveResults)
      .map(RatedResolveResult::getElement)
      .nonNull()
      .remove(element -> element instanceof PyTargetExpression)
      .map(element -> getReferenceTypeFromProviders(element, context, anchor))
      .nonNull()
      .map(r -> Objects.requireNonNull(r).get())
      .toList();

    PyType memberType = providedTypes.isEmpty()
                        ? PyTypeUtil.getTypeOfMember(resolveResults, context)
                        : PyUnionType.union(providedTypes);

    PyType specializedMemberType = PyTypeUtil.specializeMemberType(classType, selfType, memberType, context);

    final Ref<PyType> descriptorType = PyDescriptorTypeUtil.getDunderGetReturnType(anchor, selfType, specializedMemberType, context);
    if (descriptorType != null) {
      return descriptorType.get();
    }

    boolean isFunction = specializedMemberType instanceof PyCallableType && !(specializedMemberType instanceof PyClassLikeType) ||
                         specializedMemberType instanceof PyOverloadType;
    if (isFunction) {
      if (!selfType.isDefinition() && isInstanceMember(resolveResults, context)) {
        return specializedMemberType;
      }
      PyClass memberOwner = PyTypeUtil.getContainingClass(resolveResults);
      return PyTypeUtil.bindFunction(selfType, specializedMemberType, memberOwner, context, errors);
    }

    return specializedMemberType;
  }

  private static boolean isInstanceMember(@NotNull List<? extends RatedResolveResult> resolveResults,
                                          @NotNull TypeEvalContext context) {
    List<PsiElement> elements = ContainerUtil.mapNotNull(resolveResults, RatedResolveResult::getElement);
    if (elements.isEmpty()) return false;
    if (!(ScopeUtil.getScopeOwner(elements.getFirst()) instanceof PyClass)) {
      return true;
    }
    return ContainerUtil.exists(elements,
                                element -> element instanceof PyTargetExpression target &&
                                           (target.getAnnotationValue() != null || target.getTypeCommentAnnotation() != null) &&
                                           !PyTypingTypeProvider.isClassVar(target, context) &&
                                           !(PyTypingTypeProvider.isFinal(target, context) && target.hasAssignedValue()));
  }

  private @Nullable PyType getTypeFromProviders(@NotNull TypeEvalContext context) {
    for (PyTypeProvider provider : PyTypeProvider.EP_NAME.getExtensionList()) {
      final PyType type = provider.getReferenceExpressionType(this, context);
      if (type != null) {
        return type;
      }
    }
    return PyAnyType.getUnknown();
  }

  private static @Nullable Ref<PyType> getTypeFromTarget(@NotNull PsiElement target,
                                                         @NotNull TypeEvalContext context,
                                                         @NotNull PyQualifiedExpression anchor) {
    if (!(target instanceof PyTargetExpression)) {  // PyTargetExpression will ask about its type itself
      final Ref<PyType> pyType = getReferenceTypeFromProviders(target, context, anchor);
      if (pyType != null) {
        return pyType;
      }
    }
    if (target instanceof PyTargetExpression) {
      final String name = ((PyTargetExpression)target).getName();
      if (PyNames.NONE.equals(name)) {
        return Ref.create(PyBuiltinCache.getInstance(target).getNoneType());
      }
      if (PyNames.TRUE.equals(name) || PyNames.FALSE.equals(name)) {
        return Ref.create(PyBuiltinCache.getInstance(target).getBoolType());
      }
    }
    if (target instanceof PyFile) {
      return Ref.create(new PyModuleType((PyFile)target));
    }
    // If it is qualified, we already tried inferring by CFG in getQualifiedReferenceTypeByControlFlow
    if (!anchor.isQualified() && target instanceof PyElement && context.allowDataFlow(anchor)) {
      final ScopeOwner scopeOwner = ScopeUtil.getScopeOwner(anchor);
      final String name = ((PyElement)target).getName();
      if (scopeOwner != null && name != null) {
        if (!ScopeUtil.getElementsOfAccessType(name, scopeOwner, ReadWriteInstruction.ACCESS.ASSERTTYPE).isEmpty()
            || (target instanceof PyTargetExpression
                || target instanceof PyNamedParameter
                || !ScopeUtil.getElementsOfAccessType(name, scopeOwner, ReadWriteInstruction.ACCESS.READWRITE).isEmpty())
            && ScopeUtil.getScopeOwner(target) == scopeOwner) {
          final PyType type = getTypeByControlFlow(name, context, anchor, scopeOwner).type();
          if (!isUnknown(type)) {
            return Ref.create(type);
          }
        }
      }
    }
    if (target instanceof PyFunction function) {
      final PyDecoratorList decoratorList = function.getDecoratorList();
      if (decoratorList != null) {
        final PyDecorator propertyDecorator = decoratorList.findDecorator(PyNames.PROPERTY);
        if (propertyDecorator != null) {
          return Ref.create(PyBuiltinCache.getInstance(target).getObjectType(PyNames.PROPERTY));
        }
        for (PyDecorator decorator : decoratorList.getDecorators()) {
          final QualifiedName qName = decorator.getQualifiedName();
          if (qName != null && (qName.endsWith(PyNames.SETTER) || qName.endsWith(PyNames.DELETER) || qName.endsWith(PyNames.GETTER))) {
            return Ref.create(PyBuiltinCache.getInstance(target).getObjectType(PyNames.PROPERTY));
          }
        }
      }
      var overloads = PyiUtil.getOverloads(function, context);
      if (!overloads.isEmpty()) {
        return Ref.create(new PyOverloadType(
          ContainerUtil.map(overloads, overload -> (PyCallableType)context.getType(overload)),
          PyiUtil.isOverload(function, context) ? null : Ref.create(context.getType(function))
        ));
      }
    }
    if (target instanceof PyTypedElement) {
      PyType type = context.getType((PyTypedElement)target);
      // Widen literal types for non-Final instance attributes in cross-method access.
      // Same-function flow-sensitive access takes an early return via getQualifiedReferenceTypeByControlFlow
      // and never reaches this code, so widening here only applies to cross-scope resolution.
      if (anchor.isQualified()
          && target instanceof PyTargetExpression targetExpr
          && targetExpr.isQualified()
          && !PyTypingTypeProvider.isFinal(targetExpr, context)) {
        type = PyLiteralType.upcastLiteralToClass(type);
      }
      return Ref.create(type);
    }
    if (target instanceof PsiDirectory dir) {
      final PsiFile file = dir.findFile(PyNames.INIT_DOT_PY);
      if (file != null) {
        return getTypeFromTarget(file, context, anchor);
      }
      if (PyUtil.isPackage(dir, anchor)) {
        final PsiFile containingFile = anchor.getContainingFile();
        if (containingFile instanceof PyFile) {
          final QualifiedName qualifiedName = QualifiedNameFinder.findShortestImportableQName(dir);
          if (qualifiedName != null) {
            final PyImportedModule module = new PyImportedModule(null, (PyFile)containingFile, qualifiedName);
            return Ref.create(new PyImportedModuleType(module));
          }
        }
      }
    }
    return null;
  }

  private static @NotNull ControlFlowTypeResult getTypeByControlFlow(@NotNull String name,
                                                                      @NotNull TypeEvalContext context,
                                                                      @NotNull PyExpression anchor,
                                                                      @NotNull ScopeOwner scopeOwner) {
    if (!Registry.is("python.use.better.control.flow.type.inference")) {
      return getTypeByControlFlowOld(name, context, anchor, scopeOwner);
    }

    final PyAugAssignmentStatement augAssignment = PsiTreeUtil.getParentOfType(anchor, PyAugAssignmentStatement.class);
    final PyElement element = augAssignment != null ? augAssignment : anchor;

    final Instruction[] flow = ControlFlowCache.getControlFlow(scopeOwner).getInstructions();
    final int thisInstructionIdx = ControlFlowUtil.findInstructionNumberByElement(flow, element);
    if (thisInstructionIdx == -1) return new ControlFlowTypeResult(PyAnyType.getUnknown(), false);
    final Instruction thisInstruction = flow[thisInstructionIdx];

    final PyDefUseUtil.LatestDefsResult defsResult = PyDefUseUtil.getLatestDefs(scopeOwner, name, element, true, false, context);
    final List<Instruction> defs = defsResult.defs();

    // Pre-warm the types of earlier definitions so the recursive evaluation below stays shallow (PY-89956).
    warmEarlierDefinitionTypes(context, anchor, flow, name, thisInstruction.num());

    // null means empty set of possible types, Ref(null) means Any
    final @Nullable Ref<PyType> typeOfEarlierDefinitions = StreamEx.of(defs)
      .filter(def -> def.num() < thisInstruction.num())
      .map(def -> getTypeFromInstruction(context, anchor, def))
      .nonNull()
      .collect(PyTypeUtil.toUnionFromRef());

    // If earlier definitions were not found, variable may be unbound. Choose Unknown as type.
    PyType deducedType = PyTypeUtil.derefOrUnknown(typeOfEarlierDefinitions);

    final boolean foundPrefixCall = defsResult.foundPrefixCall();
    final var laterDefs = StreamEx.of(defs).filter(def -> def.num() > thisInstruction.num()).toList();
    if (laterDefs.isEmpty()) {
      return new ControlFlowTypeResult(deducedType, foundPrefixCall);
    }

    for (int i = 0; i < 50; i++) {
      final var t = deducedType;
      final @Nullable Ref<PyType> typeOfLaterDefinitions = context.assumeType(anchor, deducedType, ctx -> {
        var collect = new ArrayList<Ref<PyType>>();
        for (var def : laterDefs) {
          PyType type = null;
          if (t != null && ctx instanceof TypeEvalContextImpl.AssumptionContext assumptionCtx) {
            type = assumptionCtx.getKnownTypeForInstruction(anchor, t, def.num());
          }
          @Nullable Ref<PyType> typeRef;
          if (type == null) {
            typeRef = getTypeFromInstruction(ctx, anchor, def);
            if (typeRef != null) {
              PyType typeFromInstruction = typeRef.get();
              if (t != null && typeFromInstruction != null && ctx instanceof TypeEvalContextImpl.AssumptionContext assumptionCtx) {
                assumptionCtx.setKnownTypeForInstruction(anchor, t, def.num(), typeFromInstruction);
              }
            }
          }
          else {
            typeRef = Ref.create(type);
          }
          if (typeRef != null) {
            collect.add(typeRef);
          }
        }
        return collect.stream().collect(PyTypeUtil.toUnionFromRef());
      });

      if (typeOfLaterDefinitions == null) {
        return new ControlFlowTypeResult(deducedType, foundPrefixCall);
      }
      PyType newType = PyUnionType.union(deducedType, typeOfLaterDefinitions.get());
      if (Objects.equals(deducedType, newType)) {
        return new ControlFlowTypeResult(deducedType, foundPrefixCall);
      }
      deducedType = newType;
    }

    return new ControlFlowTypeResult(deducedType, foundPrefixCall);
  }

  /**
   * Pre-computes, in ascending control-flow order, the types of all earlier definitions of {@code name}
   * in the scope, so they are memoized in {@code context} before the recursive def-use evaluation in
   * {@link #getTypeByControlFlow} needs them.
   * <p>
   * Without this, a long linear chain of definitions where each depends on the previous one (e.g. repeated
   * {@code x = x}) is walked one definition per stack frame and overflows the stack (PY-89956): the
   * per-element {@link com.intellij.openapi.util.RecursionManager} key differs on every lap, and the lazy
   * type cache only fills on unwind -- after the descent has already reached full depth. Evaluating
   * earliest-first makes every later step hit the cache, which bounds the recursion depth. This only
   * reorders evaluation; it never changes the computed types, and short chains are left untouched.
   */
  private static void warmEarlierDefinitionTypes(@NotNull TypeEvalContext context,
                                                 @NotNull PyExpression anchor,
                                                 Instruction @NotNull [] flow,
                                                 @NotNull String name,
                                                 int thisInstructionNum) {
    if (Boolean.TRUE.equals(ourWarmingDefUseChain.get())) return;
    final List<ReadWriteInstruction> earlierWrites = StreamEx.of(flow)
      .select(ReadWriteInstruction.class)
      .filter(rw -> rw.num() < thisInstructionNum && rw.getAccess().isWriteAccess() && name.equals(rw.getName()))
      .toList();
    if (earlierWrites.size() < WARM_DEF_USE_THRESHOLD) return;
    ourWarmingDefUseChain.set(Boolean.TRUE);
    try {
      for (ReadWriteInstruction def : earlierWrites) {
        ProgressManager.checkCanceled();
        getTypeFromInstruction(context, anchor, def);
      }
    }
    finally {
      ourWarmingDefUseChain.set(Boolean.FALSE);
    }
  }

  private static @Nullable Ref<PyType> getTypeFromInstruction(@NotNull TypeEvalContext context,
                                                              @NotNull PyExpression anchor,
                                                              @NotNull Instruction instr) {
    if (instr instanceof ReadWriteInstruction readWriteInstruction) {
      return readWriteInstruction.getType(context, anchor);
    }
    if (instr instanceof ConditionalInstruction conditionalInstruction) {
      final PyType conditionType = context.getType((PyTypedElement)conditionalInstruction.getCondition());
      if (conditionType instanceof PyNarrowedType narrowedType && narrowedType.isBound()) {
        var arguments = narrowedType.getOriginal().getArguments(null);
        if (!arguments.isEmpty()) {
          var firstArgument = arguments.get(0);
          PyType type = narrowedType.getNarrowedType();
          if (firstArgument instanceof PyReferenceExpression && type != null) {
            @Nullable PyType initial = context.getType(firstArgument);
            boolean positive = conditionalInstruction.getResult() ^ narrowedType.getNegated();
            if (narrowedType.getTypeIs()) {
              return PyTypeAssertionEvaluator.createAssertionType(initial, type, positive, true, context);
            }
            return Ref.create((positive) ? type : initial);
          }
        }
      }
    }
    return null;
  }

  private static @NotNull ControlFlowTypeResult getTypeByControlFlowOld(@NotNull String name,
                                                                         @NotNull TypeEvalContext context,
                                                                         @NotNull PyExpression anchor,
                                                                         @NotNull ScopeOwner scopeOwner) {
    final PyAugAssignmentStatement augAssignment = PsiTreeUtil.getParentOfType(anchor, PyAugAssignmentStatement.class);
    final PyElement element = augAssignment != null ? augAssignment : anchor;
    final PyDefUseUtil.LatestDefsResult defsResult = PyDefUseUtil.getLatestDefs(scopeOwner, name, element, true, false, context);
    final List<Instruction> defs = defsResult.defs();
    // null means empty set of possible types, Ref(null) means Any
    final @Nullable Ref<PyType> combinedType = StreamEx.of(defs)
      .map(instr -> {
        if (instr.getElement() == anchor) {
          // exclude recursive definition (example: type of 'i++' inside a loop)
          return null;
        }
        if (instr instanceof ReadWriteInstruction readWriteInstruction) {
          return readWriteInstruction.getType(context, anchor);
        }
        if (instr instanceof ConditionalInstruction conditionalInstruction) {
          if (context.getType((PyTypedElement)conditionalInstruction.getCondition()) instanceof PyNarrowedType narrowedType
              && narrowedType.isBound()) {
            var arguments = narrowedType.getOriginal().getArguments(null);
            if (!arguments.isEmpty()) {
              var firstArgument = arguments.get(0);
              PyType type = narrowedType.getNarrowedType();
              if (firstArgument instanceof PyReferenceExpression && type != null) {
                @Nullable PyType initial = context.getType(firstArgument);
                boolean positive = conditionalInstruction.getResult() ^ narrowedType.getNegated();
                if (narrowedType.getTypeIs()) {
                  return PyTypeAssertionEvaluator.createAssertionType(initial, type, positive, false, context);
                }
                return Ref.create(positive ? type : initial);
              }
            }
          }
        }
        return null;
      })
      .nonNull()
      .collect(PyTypeUtil.toUnionFromRef());
    return new ControlFlowTypeResult(PyTypeUtil.derefOrUnknown(combinedType), defsResult.foundPrefixCall());
  }

  public static @Nullable Ref<PyType> getReferenceTypeFromProviders(@NotNull PsiElement target,
                                                                    @NotNull TypeEvalContext context,
                                                                    @Nullable PsiElement anchor) {
    for (PyTypeProvider provider : PyTypeProvider.EP_NAME.getExtensionList()) {
      final Ref<PyType> result = provider.getReferenceType(target, context, anchor);
      if (result != null) {
        return result;
      }
    }

    return null;
  }

  @Override
  public void subtreeChanged() {
    super.subtreeChanged();
    myQualifiedName = null;
  }

  private static final class MultiFollowQueueNode {

    private final @NotNull PyReferenceExpression myReferenceExpression;

    private final @NotNull List<PyExpression> myQualifiers;

    private MultiFollowQueueNode(@NotNull PyReferenceExpression referenceExpression, @NotNull List<PyExpression> qualifiers) {
      myReferenceExpression = referenceExpression;
      myQualifiers = qualifiers;
    }

    public static @NotNull MultiFollowQueueNode create(@Nullable MultiFollowQueueNode previous,
                                                       @NotNull PyReferenceExpression referenceExpression) {
      final PyExpression qualifier = referenceExpression.getQualifier();
      final List<PyExpression> previousQualifiers = previous == null ? Collections.emptyList() : previous.myQualifiers;
      final List<PyExpression> newQualifiers = qualifier == null ? previousQualifiers : ContainerUtil.append(previousQualifiers, qualifier);

      return new MultiFollowQueueNode(referenceExpression, newQualifiers);
    }
  }
}
