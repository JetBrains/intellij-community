// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.psi.impl;

import com.intellij.codeInsight.controlflow.ConditionalInstruction;
import com.intellij.codeInsight.controlflow.ControlFlowUtil;
import com.intellij.codeInsight.controlflow.Instruction;
import com.intellij.diagnostic.PluginException;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.diagnostic.Logger;
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
import com.jetbrains.python.ast.PyAstFunction;
import com.jetbrains.python.codeInsight.controlflow.ControlFlowCache;
import com.jetbrains.python.codeInsight.controlflow.PyTypeAssertionEvaluator;
import com.jetbrains.python.codeInsight.controlflow.ReadWriteInstruction;
import com.jetbrains.python.codeInsight.controlflow.ScopeOwner;
import com.jetbrains.python.codeInsight.dataflow.scope.ScopeUtil;
import com.jetbrains.python.psi.AccessDirection;
import com.jetbrains.python.psi.Property;
import com.jetbrains.python.psi.PyAnnotationOwner;
import com.jetbrains.python.psi.PyAugAssignmentStatement;
import com.jetbrains.python.psi.PyCallExpression;
import com.jetbrains.python.psi.PyCallable;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyDecorator;
import com.jetbrains.python.psi.PyDecoratorList;
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
import com.jetbrains.python.psi.types.PyDescriptorTypeUtil;
import com.jetbrains.python.psi.types.PyImportedModuleType;
import com.jetbrains.python.psi.types.PyModuleType;
import com.jetbrains.python.psi.types.PyNarrowedType;
import com.jetbrains.python.psi.types.PyOverloadType;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.PyTypeChecker;
import com.jetbrains.python.psi.types.PyTypeParameterType;
import com.jetbrains.python.psi.types.PyTypeUtil;
import com.jetbrains.python.psi.types.PyUnionType;
import com.jetbrains.python.psi.types.PyUnsafeUnionType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import com.jetbrains.python.psi.types.TypeEvalContextImpl;
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
import java.util.Queue;
import java.util.Set;
import java.util.function.Predicate;

import static com.jetbrains.python.psi.types.PyNoneTypeKt.isNoneType;
import static com.jetbrains.python.psi.types.PyTypeUtilKt.isUnknown;

/**
 * Implements reference expression PSI.
 */
public class PyReferenceExpressionImpl extends PyElementImpl implements PyReferenceExpression {

  private static final Logger LOG = Logger.getInstance(PyReferenceExpressionImpl.class);

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
    final PsiElement importParent = PsiTreeUtil.getParentOfType(this, PyImportElement.class, PyFromImportStatement.class);
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
    final boolean qualified = isQualified();

    final PyType providedType = getTypeFromProviders(context);
    if (!isUnknown(providedType)) {
      return providedType;
    }

    if (qualified) {
      final Ref<PyType> qualifiedReferenceType = getQualifiedReferenceType(context);
      if (qualifiedReferenceType != null) {
        return qualifiedReferenceType.get();
      }
    }

    // null means no result; Ref(null) here can mean that a variable is annotated
    // like `var: Any` earlier, so we know not to use __getattr__ later
    final Ref<PyType> typeFromTargetsRef = getTypeFromTargets(context);
    final PyType typeFromTargets = PyTypeUtil.derefOrUnknown(typeFromTargetsRef);
    if (qualified && isNoneType(typeFromTargets) && !isTargetAnnotated(context)) {
      /* we support a special case where we convert an unannotated attribute of `None` to `UnsafeUnion[None, Unknown]`
        this is because there are frequently cases in real code where inferring `None` would lead to undesirable false positives:
        ```py
        class C:
            def __init__(self):
                self.a = None  # user intends `int | None` / `late int`
            def set_a(self):
                self.a = 1
        def f(c: C):
            c.a + 1  # FP here
        ```

        we use `UnsafeUnion` to avoid cases where the `None` doesn't typically surface to usages,
        if the user is interested in typing they should always annotate an attribute that is initialised with `None`

        there is also a consideration for the case where a base class sets an attribute with `None`, expecting it to be
        overridden with a value
      */
      return PyUnsafeUnionType.unsafeUnion(typeFromTargets, PyAnyType.getUnknown());
    }

    final Ref<PyType> descriptorType = PyDescriptorTypeUtil.getDunderGetReturnType(this, typeFromTargets, context);
    if (descriptorType != null) {
      return descriptorType.get();
    }
    final PyType callableType = getCallableType(context);
    if (callableType != null) {
      return callableType;
    }

    if (typeFromTargetsRef == null && qualified) {
      return getTypeFromDunderGetAttr(context);
    }

    return typeFromTargets;
  }

  private @Nullable PyType getCallableType(@NotNull TypeEvalContext context) {
    PyCallExpression callExpression = PyCallExpressionNavigator.getPyCallExpressionByCallee(this);
    if (callExpression != null) {
      return PyCallExpressionHelper.getCalleeType(callExpression, PyResolveContext.defaultContext(context));
    }
    return null;
  }

  private @Nullable Ref<PyType> getQualifiedReferenceType(@NotNull TypeEvalContext context) {
    if (!context.maySwitchToAST(this)) {
      return null;
    }

    final PyExpression qualifier = getQualifier();
    if (qualifier == null) return null;

    final String attrName = getName();
    if (attrName == null) return null;

    final PyType qualifierType = context.getType(qualifier);

    final Ref<PyType> typeOfProperty = getTypeOfProperty(qualifierType, attrName, context);
    if (typeOfProperty != null) {
      return typeOfProperty;
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

    final ControlFlowTypeResult controlFlowResult = getQualifiedReferenceTypeByControlFlow(context);
    final PyType typeByControlFlow = controlFlowResult.type();
    if (typeByControlFlow != null) {
      if (controlFlowResult.foundPrefixCall()) {
        // A call with prefix as receiver/argument may or may not mutate it, so return UnsafeUnion of narrowed and declared types (PY-88265)
        PyType declaredType = Ref.deref(getTypeFromTargets(context));
        if (isNoneType(declaredType)) {
          declaredType = PyAnyType.getUnknown();
        }
        return Ref.create(PyUnsafeUnionType.unsafeUnion(typeByControlFlow, declaredType));
      }
      return Ref.create(typeByControlFlow);
    }

    return null;
  }

  private @Nullable PyType getTypeFromDunderGetAttr(@NotNull TypeEvalContext context) {
    final PyExpression qualifier = getQualifier();
    assert qualifier != null;

    if (context.getType(qualifier) instanceof PyClassLikeType classLikeType) {
      final ResolveResult getattr = ContainerUtil.getFirstItem(
        classLikeType.resolveMember(PyNames.GETATTR, qualifier, AccessDirection.READ, PyResolveContext.defaultContext(context)));
      if (getattr != null && getattr.getElement() instanceof PyCallable method) {
        return context.getReturnType(method);
      }
    }
    return PyAnyType.getUnknown();
  }

  private boolean isTargetAnnotated(@NotNull TypeEvalContext context) {
    final PyResolveContext resolveContext = PyResolveContext.defaultContext(context);
    for (PsiElement target : PyUtil.multiResolveTopPriority(getReference(resolveContext))) {
      if (target instanceof PyAnnotationOwner owner && owner.getAnnotation() != null) {
        return true;
      }
    }
    return false;
  }

  private @Nullable Ref<PyType> getTypeFromTargets(@NotNull TypeEvalContext context) {
    final PyResolveContext resolveContext = PyResolveContext.defaultContext(context);
    final List<Ref<PyType>> members = new ArrayList<>();

    final PsiFile realFile = FileContextUtil.getContextFile(this);
    if (!(getContainingFile() instanceof PyExpressionCodeFragment) || (realFile != null && context.maySwitchToAST(realFile))) {
      final var overloadMembers = new ArrayList<>();
      for (PsiElement target : PyUtil.multiResolveTopPriority(getReference(resolveContext))) {
        if (target == this) {
          continue;
        }

        if (overloadMembers.contains(target)) {
          continue;
        }

        if (!target.isValid()) {
          throw new PsiInvalidElementAccessException(this);
        }

        var member = getTypeFromTarget(target, context, this);
        if (Ref.deref(member) instanceof PyOverloadType && target instanceof PyFunction function) {
          overloadMembers.addAll(PyiUtil.getOverloads(function, context));
        }
        members.add(member);
      }
    }

    return members.stream().collect(PyTypeUtil.toUnionFromRef());
  }

  private @NotNull ControlFlowTypeResult getQualifiedReferenceTypeByControlFlow(@NotNull TypeEvalContext context) {
    PyExpression qualifier = getQualifier();
    if (context.allowDataFlow(this) && qualifier != null) {
      PyExpression next = qualifier;
      while (next != null) {
        qualifier = next;
        next = qualifier instanceof PyQualifiedExpression ? ((PyQualifiedExpression)qualifier).getQualifier() : null;
      }
      final ScopeOwner scopeOwner = ScopeUtil.getScopeOwner(this);
      final QualifiedName qname = asQualifiedName();
      if (qname != null && scopeOwner != null) {
        return getTypeByControlFlow(qname.toString(), context, qualifier, scopeOwner);
      }
    }
    return new ControlFlowTypeResult(null, false);
  }

  private @Nullable Ref<PyType> getTypeOfProperty(@Nullable PyType qualifierType, @NotNull String name, @NotNull TypeEvalContext context) {
    if (qualifierType instanceof PyClassType classType) {
      final PyClass pyClass = classType.getPyClass();
      final Property property = pyClass.findProperty(name, true, context);

      if (property != null) {
        if (classType.isDefinition()) {
          return Ref.create(PyBuiltinCache.getInstance(pyClass).getObjectType(PyNames.PROPERTY));
        }
        if (AccessDirection.of(this) == AccessDirection.READ) {
          final PyType type = property.getType(getQualifier(), context);
          if (type != null) {
            return Ref.create(type);
          }
        }
        return Ref.create();
      }
    }
    else if (qualifierType instanceof PyUnionType unionType) {
      for (PyType type : unionType.getMembers()) {
        final Ref<PyType> result = getTypeOfProperty(type, name, context);
        if (result != null) {
          return result;
        }
      }
    }
    else if (qualifierType instanceof PyUnsafeUnionType unionType) {
      for (PyType type : unionType.getMembers()) {
        final Ref<PyType> result = getTypeOfProperty(type, name, context);
        if (result != null) {
          return result;
        }
      }
    }
    else if (qualifierType instanceof PyTypeParameterType typeParameterType) {
      final PyType effectiveBound = typeParameterType.getEffectiveBound();
      if (effectiveBound != null && effectiveBound != qualifierType) {
        return getTypeOfProperty(effectiveBound, name, context);
      }
    }

    return null;
  }

  private @Nullable PyType getTypeFromProviders(@NotNull TypeEvalContext context) {
    for (PyTypeProvider provider : PyTypeProvider.EP_NAME.getExtensionList()) {
      try {
        final PyType type = provider.getReferenceExpressionType(this, context);
        if (type != null) {
          return type;
        }
      }
      catch (AbstractMethodError e) {
        LOG.info(PluginException.createByClass("Failed to get expression type via " + provider.getClass(), e, provider.getClass()));
      }
    }
    return PyAnyType.getUnknown();
  }

  private static @Nullable Ref<PyType> getTypeFromTarget(@NotNull PsiElement target,
                                                         @NotNull TypeEvalContext context,
                                                         @NotNull PyReferenceExpression anchor) {
    final @Nullable Ref<PyType> typeRef = dropSelfForQualifiedMethod(getGenericTypeFromTarget(target, context, anchor), context, anchor);

    if (context.maySwitchToAST(anchor)) {
      final PyExpression qualifier = anchor.getQualifier();
      if (qualifier != null) {
        PyType qualifierType = context.getType(qualifier);
        boolean possiblyParameterizedQualifier = !(qualifierType instanceof PyModuleType || qualifierType instanceof PyImportedModuleType);
        final PyType type = Ref.deref(typeRef);
        if (possiblyParameterizedQualifier && PyTypeChecker.hasGenerics(type, context)) {
          var substitutions = PyTypeChecker.unifyReceiver(qualifierType, context);
          PyType typeWithSubstitutions = PyTypeChecker.substitute(type, substitutions, context);
          return Ref.create(typeWithSubstitutions);
        }
      }
    }

    return typeRef;
  }

  private static @Nullable Ref<PyType> getGenericTypeFromTarget(@NotNull PsiElement target,
                                                                @NotNull TypeEvalContext context,
                                                                @NotNull PyReferenceExpression anchor) {
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
      return Ref.create(context.getType((PyTypedElement)target));
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

  private static @Nullable Ref<PyType> dropSelfForQualifiedMethod(@Nullable Ref<PyType> type,
                                                                  @NotNull TypeEvalContext context,
                                                                  @NotNull PyReferenceExpression anchor) {
    if (Ref.deref(type) instanceof PyCallableType functionType && context.maySwitchToAST(anchor) && anchor.getQualifier() != null) {
      if (context.getType(anchor.getQualifier()) instanceof PyClassLikeType classLikeType && classLikeType.isDefinition() &&
          functionType.getModifier() != PyAstFunction.Modifier.CLASSMETHOD) {
        return type;
      }
      return Ref.create(functionType.dropSelf(context));
    }

    return type;
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
    if (thisInstructionIdx == -1) return new ControlFlowTypeResult(null, false);
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
      .sortedByInt(Instruction::num)
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
