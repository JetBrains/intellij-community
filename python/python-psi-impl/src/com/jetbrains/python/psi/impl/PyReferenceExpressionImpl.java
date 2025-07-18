// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.psi.impl;

import com.intellij.codeInsight.controlflow.ConditionalInstruction;
import com.intellij.codeInsight.controlflow.ControlFlowUtil;
import com.intellij.codeInsight.controlflow.Instruction;
import com.intellij.diagnostic.PluginException;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.psi.*;
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
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.references.PyImportReference;
import com.jetbrains.python.psi.impl.references.PyQualifiedReference;
import com.jetbrains.python.psi.impl.references.PyReferenceImpl;
import com.jetbrains.python.psi.resolve.*;
import com.jetbrains.python.psi.types.*;
import com.jetbrains.python.refactoring.PyDefUseUtil;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Predicate;

import static com.jetbrains.python.psi.impl.PyCallExpressionHelper.getCalleeType;
import static com.jetbrains.python.psi.types.PyNoneTypeKt.isNoneType;

/**
 * Implements reference expression PSI.
 */
public class PyReferenceExpressionImpl extends PyElementImpl implements PyReferenceExpression {

  private static final Logger LOG = Logger.getInstance(PyReferenceExpressionImpl.class);

  private static final int MAX_CFG_ITERATIONS = 30;

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
    if (providedType != null) {
      return providedType;
    }

    if (qualified) {
      final Ref<PyType> qualifiedReferenceType = getQualifiedReferenceType(context);
      if (qualifiedReferenceType != null) {
        return qualifiedReferenceType.get();
      }
    }

    final PyType typeFromTargets = getTypeFromTargets(context);
    if (qualified && isNoneType(typeFromTargets)) {
      return null;
    }
    final Ref<PyType> descriptorType = PyDescriptorTypeUtil.getDunderGetReturnType(this, typeFromTargets, context);
    if (descriptorType != null) {
      return descriptorType.get();
    }

    final PyType callableType = getCallableType(context, key);
    if (callableType != null) {
      return callableType;
    }

    if (typeFromTargets == null && qualified) {
      return getTypeFromDunderGetAttr(context);
    }

    return typeFromTargets;
  }

  private @Nullable PyType getCallableType(@NotNull TypeEvalContext context, @NotNull TypeEvalContext.Key key) {
    PyCallExpression callExpression = PyCallExpressionNavigator.getPyCallExpressionByCallee(this);
    if (callExpression != null) {
      return getCalleeType(callExpression, PyResolveContext.defaultContext(context));
    }
    return null;
  }

  private @Nullable Ref<PyType> getQualifiedReferenceType(@NotNull TypeEvalContext context) {
    if (!context.maySwitchToAST(this)) {
      return null;
    }

    final PyType maybe_type = PyUtil.getSpecialAttributeType(this, context);
    if (maybe_type != null) return Ref.create(maybe_type);

    final Ref<PyType> typeOfProperty = getTypeOfProperty(context);
    if (typeOfProperty != null) {
      return typeOfProperty;
    }

    final PyType typeByControlFlow = getQualifiedReferenceTypeByControlFlow(context);
    if (typeByControlFlow != null) {
      return Ref.create(typeByControlFlow);
    }

    return null;
  }

  private @Nullable PyType getTypeFromDunderGetAttr(@NotNull TypeEvalContext context) {
    final PyExpression qualifier = getQualifier();
    assert qualifier != null;

    final PyType qType = context.getType(qualifier);
    if (qType instanceof PyClassType classType) {
      final ResolveResult getattr = ContainerUtil.getFirstItem(
        classType.resolveMember(PyNames.GETATTR, qualifier, AccessDirection.READ, PyResolveContext.defaultContext(context)));
      if (getattr != null && getattr.getElement() instanceof PyCallable method) {
        return context.getReturnType(method);
      }
    }
    return null;
  }

  private @Nullable PyType getTypeFromTargets(@NotNull TypeEvalContext context) {
    final PyResolveContext resolveContext = PyResolveContext.defaultContext(context);
    final List<PyType> members = new ArrayList<>();

    final PsiFile realFile = FileContextUtil.getContextFile(this);
    if (!(getContainingFile() instanceof PyExpressionCodeFragment) || (realFile != null && context.maySwitchToAST(realFile))) {
      for (PsiElement target : PyUtil.multiResolveTopPriority(getReference(resolveContext))) {
        if (target == this) {
          continue;
        }

        if (!target.isValid()) {
          throw new PsiInvalidElementAccessException(this);
        }

        members.add(getTypeFromTarget(target, context, this));
      }
    }

    return PyUnionType.union(members);
  }

  private @Nullable PyType getQualifiedReferenceTypeByControlFlow(@NotNull TypeEvalContext context) {
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
    return null;
  }

  private @Nullable Ref<PyType> getTypeOfProperty(@NotNull TypeEvalContext context) {
    final PyExpression qualifier = getQualifier();
    final String name = getName();
    if (name != null && qualifier != null) {
      final PyType qualifierType = context.getType(qualifier);
      return getTypeOfProperty(qualifierType, name, context);
    }
    return null;
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
    return null;
  }

  private static @Nullable PyType getTypeFromTarget(@NotNull PsiElement target,
                                                    @NotNull TypeEvalContext context,
                                                    @NotNull PyReferenceExpression anchor) {
    final PyType type = dropSelfForQualifiedMethod(getGenericTypeFromTarget(target, context, anchor), context, anchor);

    if (context.maySwitchToAST(anchor)) {
      final PyExpression qualifier = anchor.getQualifier();
      if (qualifier != null) {
        PyType qualifierType = context.getType(qualifier);
        boolean possiblyParameterizedQualifier = !(qualifierType instanceof PyModuleType || qualifierType instanceof PyImportedModuleType);
        if (possiblyParameterizedQualifier && PyTypeChecker.hasGenerics(type, context)) {
          if (qualifierType instanceof PyCollectionType collectionType && collectionType.isDefinition()) {
            if (type != null) {
              var substitutions = PyTypeChecker.unifyReceiver(qualifierType, context);
              PyType typeWithSubstitutions = PyTypeChecker.substitute(type, substitutions, context);
              if (typeWithSubstitutions != null) {
                return typeWithSubstitutions;
              }
            }
          }
          final var substitutions = PyTypeChecker.unifyGenericCall(qualifier, Collections.emptyMap(), context);
          if (substitutions != null) {
            return PyTypeChecker.substitute(type, substitutions, context);
          }
        }
      }
    }

    return type;
  }

  private static @Nullable PyType getGenericTypeFromTarget(@NotNull PsiElement target,
                                                           @NotNull TypeEvalContext context,
                                                           @NotNull PyReferenceExpression anchor) {
    if (!(target instanceof PyTargetExpression)) {  // PyTargetExpression will ask about its type itself
      final Ref<PyType> pyType = getReferenceTypeFromProviders(target, context, anchor);
      if (pyType != null) {
        return pyType.get();
      }
    }
    if (target instanceof PyTargetExpression) {
      final String name = ((PyTargetExpression)target).getName();
      if (PyNames.NONE.equals(name)) {
        return PyBuiltinCache.getInstance(target).getNoneType();
      }
      if (PyNames.TRUE.equals(name) || PyNames.FALSE.equals(name)) {
        return PyBuiltinCache.getInstance(target).getBoolType();
      }
    }
    if (target instanceof PyFile) {
      return new PyModuleType((PyFile)target);
    }
    if (target instanceof PyElement && context.allowDataFlow(anchor)) {
      final ScopeOwner scopeOwner = ScopeUtil.getScopeOwner(anchor);
      final String name = ((PyElement)target).getName();
      if (scopeOwner != null &&
          name != null &&
          (!ScopeUtil.getElementsOfAccessType(name, scopeOwner, ReadWriteInstruction.ACCESS.ASSERTTYPE).isEmpty()
            || target instanceof PyTargetExpression || target instanceof PyNamedParameter)) {
        final PyType type = getTypeByControlFlow(name, context, anchor, scopeOwner);
        if (type != null) {
          return type;
        }
      }
    }
    if (target instanceof PyFunction) {
      final PyDecoratorList decoratorList = ((PyFunction)target).getDecoratorList();
      if (decoratorList != null) {
        final PyDecorator propertyDecorator = decoratorList.findDecorator(PyNames.PROPERTY);
        if (propertyDecorator != null) {
          return PyBuiltinCache.getInstance(target).getObjectType(PyNames.PROPERTY);
        }
        for (PyDecorator decorator : decoratorList.getDecorators()) {
          final QualifiedName qName = decorator.getQualifiedName();
          if (qName != null && (qName.endsWith(PyNames.SETTER) || qName.endsWith(PyNames.DELETER) || qName.endsWith(PyNames.GETTER))) {
            return PyBuiltinCache.getInstance(target).getObjectType(PyNames.PROPERTY);
          }
        }
      }
    }
    if (target instanceof PyTypedElement) {
      return context.getType((PyTypedElement)target);
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
            return new PyImportedModuleType(module);
          }
        }
      }
    }
    return null;
  }

  private static @Nullable PyType dropSelfForQualifiedMethod(@Nullable PyType type,
                                                             @NotNull TypeEvalContext context,
                                                             @NotNull PyReferenceExpression anchor) {
    if (type instanceof PyFunctionType functionType && context.maySwitchToAST(anchor) && anchor.getQualifier() != null) {
      PyType qualifierType = context.getType(anchor.getQualifier());
      if (qualifierType instanceof PyClassLikeType classLikeType && classLikeType.isDefinition() &&
          functionType.getModifier() != PyAstFunction.Modifier.CLASSMETHOD) {
        return type;
      }
      return functionType.dropSelf(context);
    }

    return type;
  }

  private static PyType getTypeByControlFlow(@NotNull String name,
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
    if (thisInstructionIdx == -1) return null;
    final Instruction thisInstruction = flow[thisInstructionIdx];

    final List<Instruction> defs = PyDefUseUtil.getLatestDefs(scopeOwner, name, element, true, false, context);

    // null means empty set of possible types, Ref(null) means Any
    final @Nullable Ref<PyType> typeOfEarlierDefinitions = StreamEx.of(defs)
      .filter(def -> def.num() < thisInstruction.num())
      .map(def -> getTypeFromInstruction(context, anchor, def))
      .nonNull()
      .collect(PyTypeUtil.toUnionFromRef());

    // If earlier definitions were not found, variable may be unbound. Choose Any as type.
    PyType deducedType = Ref.deref(typeOfEarlierDefinitions);

    final var laterDefs = StreamEx.of(defs).filter(def -> def.num() > thisInstruction.num()).toList();
    if (laterDefs.isEmpty()) {
      return deducedType;
    }
    
    for (int i = 0; i < MAX_CFG_ITERATIONS; i++) {
      final @Nullable Ref<PyType> typeOfLaterDefinitions = context.assumeType(anchor, deducedType, (ctx) -> {
        return StreamEx.of(laterDefs)
          .map(def -> getTypeFromInstruction(ctx, anchor, def))
          .nonNull()
          .collect(PyTypeUtil.toUnionFromRef());
      });

      if (typeOfLaterDefinitions == null) {
        return deducedType;
      }
      PyType newType = PyUnionType.union(deducedType, typeOfLaterDefinitions.get());
      if (Objects.equals(deducedType, newType)) {
        return deducedType;
      }
      deducedType = newType;
    }

    return deducedType;
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
              return PyTypeAssertionEvaluator.createAssertionType(initial, type, positive, context);
            }
            return Ref.create((positive) ? type : initial);
          }
        }
      }
    }
    return null;
  }

  private static PyType getTypeByControlFlowOld(@NotNull String name,
                                                @NotNull TypeEvalContext context,
                                                @NotNull PyExpression anchor,
                                                @NotNull ScopeOwner scopeOwner) {
    final PyAugAssignmentStatement augAssignment = PsiTreeUtil.getParentOfType(anchor, PyAugAssignmentStatement.class);
    final PyElement element = augAssignment != null ? augAssignment : anchor;
    try {
      final List<Instruction> defs = PyDefUseUtil.getLatestDefs(scopeOwner, name, element, true, false, context);
      // null means empty set of possible types, Ref(null) means Any
      final @Nullable Ref<PyType> combinedType = StreamEx.of(defs)
        .map(instr -> {
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
                    return PyTypeAssertionEvaluator.createAssertionType(initial, type, positive, context);
                  }
                  return Ref.create((positive) ? type : initial);
                }
              }
            }
          }
          return null;
        })
        .nonNull()
        .collect(PyTypeUtil.toUnionFromRef());
      return Ref.deref(combinedType);
    }
    catch (PyDefUseUtil.InstructionNotFoundException ignored) {
    }
    return null;
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

    public static @NotNull MultiFollowQueueNode create(@Nullable MultiFollowQueueNode previous, @NotNull PyReferenceExpression referenceExpression) {
      final PyExpression qualifier = referenceExpression.getQualifier();
      final List<PyExpression> previousQualifiers = previous == null ? Collections.emptyList() : previous.myQualifiers;
      final List<PyExpression> newQualifiers = qualifier == null ? previousQualifiers : ContainerUtil.append(previousQualifiers, qualifier);

      return new MultiFollowQueueNode(referenceExpression, newQualifiers);
    }
  }
}
