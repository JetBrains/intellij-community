/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

import com.intellij.codeInsight.controlflow.Instruction;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.ExtensionException;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.resolve.FileContextUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.QualifiedName;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.PythonDialectsTokenSetProvider;
import com.jetbrains.python.codeInsight.controlflow.ReadWriteInstruction;
import com.jetbrains.python.codeInsight.controlflow.ScopeOwner;
import com.jetbrains.python.codeInsight.dataflow.scope.ScopeUtil;
import com.jetbrains.python.console.PydevConsoleRunner;
import com.jetbrains.python.console.completion.PydevConsoleReference;
import com.jetbrains.python.console.pydev.ConsoleCommunication;
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

import static com.jetbrains.python.psi.PyUtil.as;

/**
 * Implements reference expression PSI.
 *
 * @author yole
 */
public class PyReferenceExpressionImpl extends PyElementImpl implements PyReferenceExpression {

  private static final Logger LOG = Logger.getInstance("#com.jetbrains.python.psi.impl.PyReferenceExpressionImpl");

  @Nullable private volatile QualifiedName myQualifiedName = null;

  public PyReferenceExpressionImpl(@NotNull ASTNode astNode) {
    super(astNode);
  }

  @NotNull
  @Override
  public PsiPolyVariantReference getReference() {
    //noinspection InstanceofIncompatibleInterface
    assert !(this instanceof StubBasedPsiElement);
    final TypeEvalContext context = TypeEvalContext.codeAnalysis(getProject(), getContainingFile());
    return getReference(PyResolveContext.defaultContext().withTypeEvalContext(context));
  }

  @NotNull
  @Override
  public PsiPolyVariantReference getReference(@NotNull PyResolveContext context) {
    // Handle import reference
    final PsiElement importParent = PsiTreeUtil.getParentOfType(this, PyImportElement.class, PyFromImportStatement.class);
    if (importParent != null) {
      return PyImportReference.forElement(this, importParent, context);
    }

    final PyExpression qualifier = getQualifier();

    // Return special reference
    final ConsoleCommunication communication = getContainingFile().getCopyableUserData(PydevConsoleRunner.CONSOLE_KEY);
    if (communication != null) {
      final String prefix = qualifier == null ? "" : qualifier.getText() + ".";
      return new PydevConsoleReference(this, communication, prefix, context.allowRemote());
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
  @Nullable
  public PyExpression getQualifier() {
    final ASTNode[] nodes = getNode().getChildren(PythonDialectsTokenSetProvider.INSTANCE.getExpressionTokens());
    return (PyExpression)(nodes.length == 1 ? nodes[0].getPsi() : null);
  }

  @Override
  public boolean isQualified() {
    return getQualifier() != null;
  }

  @Override
  @Nullable
  public String getReferencedName() {
    final ASTNode nameElement = getNameElement();
    return nameElement != null ? nameElement.getText() : null;
  }

  @Override
  @Nullable
  public ASTNode getNameElement() {
    return getNode().findChildByType(PyTokenTypes.IDENTIFIER);
  }

  @Nullable
  @Override
  public String getName() {
    return getReferencedName();
  }

  @Override
  @NotNull
  public QualifiedResolveResult followAssignmentsChain(@NotNull PyResolveContext resolveContext) {
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

  @NotNull
  @Override
  public List<QualifiedRatedResolveResult> multiFollowAssignmentsChain(@NotNull PyResolveContext resolveContext) {
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
        if (element instanceof PyTargetExpression) {
          final PyTargetExpression target = (PyTargetExpression)element;

          final PsiElement assignedFrom;
          if (context.maySwitchToAST(target)) {
            assignedFrom = target.findAssignedValue();
          }
          else {
            assignedFrom = target.resolveAssignedValue(resolveContext);
          }

          if (assignedFrom instanceof PyReferenceExpression) {
            final PyReferenceExpression assignedReference = (PyReferenceExpression)assignedFrom;

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
        else if (element instanceof PyElement && resolveResult.isValidResult()) {
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
  @Nullable
  public QualifiedName asQualifiedName() {
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
  @Nullable
  public PyType getType(@NotNull TypeEvalContext context, @NotNull TypeEvalContext.Key key) {
    if (!TypeEvalStack.mayEvaluate(this)) {
      return null;
    }

    try {
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
      if (qualified && typeFromTargets instanceof PyNoneType) {
        return null;
      }
      final Ref<PyType> descriptorType = getDescriptorType(typeFromTargets, context);
      if (descriptorType != null) {
        return descriptorType.get();
      }
      return typeFromTargets;
    }
    finally {
      TypeEvalStack.evaluated(this);
    }
  }

  @Nullable
  private Ref<PyType> getDescriptorType(@Nullable PyType typeFromTargets, @NotNull TypeEvalContext context) {
    if (!isQualified()) return null;
    final PyClassLikeType targetType = as(typeFromTargets, PyClassLikeType.class);
    if (targetType == null) return null;
    final PyResolveContext resolveContext = PyResolveContext.noProperties().withTypeEvalContext(context);
    final List<? extends RatedResolveResult> members = targetType.resolveMember(PyNames.GET, this, AccessDirection.READ,
                                                                                resolveContext);
    if (members == null || members.isEmpty()) return null;
    final List<PyType> types = StreamEx.of(members)
      .map((result) -> result.getElement())
      .select(PyCallable.class)
      .map((callable) -> context.getReturnType(callable))
      .toList();
    final PyType type = PyUnionType.union(types);
    return Ref.create(type);
  }

  @Nullable
  private Ref<PyType> getQualifiedReferenceType(@NotNull TypeEvalContext context) {
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

  @Nullable
  private PyType getTypeFromTargets(@NotNull TypeEvalContext context) {
    final PyResolveContext resolveContext = PyResolveContext.noImplicits().withTypeEvalContext(context);
    final List<PyType> members = new ArrayList<>();

    final PsiFile realFile = FileContextUtil.getContextFile(this);
    if (!(getContainingFile() instanceof PyExpressionCodeFragment) || (realFile != null && context.maySwitchToAST(realFile))) {
      for (PsiElement target : PyUtil.multiResolveTopPriority(getReference(resolveContext))) {
        if (target == this || target == null) {
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

  @Nullable
  private PyType getQualifiedReferenceTypeByControlFlow(@NotNull TypeEvalContext context) {
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

  @Nullable
  private Ref<PyType> getTypeOfProperty(@NotNull TypeEvalContext context) {
    final PyExpression qualifier = getQualifier();
    final String name = getName();
    if (name != null && qualifier != null) {
      final PyType qualifierType = context.getType(qualifier);
      return getTypeOfProperty(qualifierType, name, context);
    }
    return null;
  }

  @Nullable
  private Ref<PyType> getTypeOfProperty(@Nullable PyType qualifierType, @NotNull String name, @NotNull TypeEvalContext context) {
    if (qualifierType instanceof PyClassType) {
      final PyClassType classType = (PyClassType)qualifierType;
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
    else if (qualifierType instanceof PyUnionType) {
      final PyUnionType unionType = (PyUnionType)qualifierType;
      for (PyType type : unionType.getMembers()) {
        final Ref<PyType> result = getTypeOfProperty(type, name, context);
        if (result != null) {
          return result;
        }
      }
    }

    return null;
  }

  @Nullable
  private PyType getTypeFromProviders(@NotNull TypeEvalContext context) {
    for (PyTypeProvider provider : Extensions.getExtensions(PyTypeProvider.EP_NAME)) {
      try {
        final PyType type = provider.getReferenceExpressionType(this, context);
        if (type != null) {
          return type;
        }
      }
      catch (AbstractMethodError e) {
        LOG.info(new ExtensionException(provider.getClass()));
      }
    }
    return null;
  }

  @Nullable
  private static PyType getTypeFromTarget(@NotNull PsiElement target,
                                          @NotNull TypeEvalContext context,
                                          @NotNull PyReferenceExpression anchor) {
    final PyType type = dropSelfForQualifiedMethod(getGenericTypeFromTarget(target, context, anchor), context, anchor);

    if (context.maySwitchToAST(anchor)) {
      final PyExpression qualifier = anchor.getQualifier();
      if (qualifier != null && PyTypeChecker.hasGenerics(type, context)) {
        final Map<PyGenericType, PyType> substitutions = PyTypeChecker.unifyGenericCall(qualifier, Collections.emptyMap(), context);
        if (!ContainerUtil.isEmpty(substitutions)) {
          final PyType substituted = PyTypeChecker.substitute(type, substitutions, context);
          if (substituted != null) {
            return substituted;
          }
        }
      }
    }

    return type;
  }

  @Nullable
  private static PyType getGenericTypeFromTarget(@NotNull PsiElement target,
                                                 @NotNull TypeEvalContext context,
                                                 @NotNull PyReferenceExpression anchor) {
    if (!(target instanceof PyTargetExpression)) {  // PyTargetExpression will ask about its type itself
      final PyType pyType = getReferenceTypeFromProviders(target, context, anchor);
      if (pyType != null) {
        return pyType;
      }
    }
    if (target instanceof PyTargetExpression) {
      final String name = ((PyTargetExpression)target).getName();
      if (PyNames.NONE.equals(name)) {
        return PyNoneType.INSTANCE;
      }
      if (PyNames.TRUE.equals(name) || PyNames.FALSE.equals(name)) {
        return PyBuiltinCache.getInstance(target).getBoolType();
      }
    }
    if (target instanceof PyFile) {
      return new PyModuleType((PyFile)target);
    }
    if ((target instanceof PyTargetExpression || target instanceof PyNamedParameter) && context.allowDataFlow(anchor)) {
      final ScopeOwner scopeOwner = ScopeUtil.getScopeOwner(anchor);
      if (scopeOwner != null && scopeOwner == ScopeUtil.getScopeOwner(target)) {
        final String name = ((PyElement)target).getName();
        if (name != null) {
          final PyType type = getTypeByControlFlow(name, context, anchor, scopeOwner);
          if (type != null) {
            return type;
          }
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
    if (target instanceof PsiDirectory) {
      final PsiDirectory dir = (PsiDirectory)target;
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

  @Nullable
  private static PyType dropSelfForQualifiedMethod(@Nullable PyType type,
                                                   @NotNull TypeEvalContext context,
                                                   @NotNull PyReferenceExpression anchor) {
    if (type instanceof PyFunctionType && context.maySwitchToAST(anchor) && anchor.getQualifier() != null) {
       return ((PyFunctionType)type).dropSelf(context);
    }

    return type;
  }

  private static PyType getTypeByControlFlow(@NotNull String name,
                                             @NotNull TypeEvalContext context,
                                             @NotNull PyExpression anchor,
                                             @NotNull ScopeOwner scopeOwner) {
    final PyAugAssignmentStatement augAssignment = PsiTreeUtil.getParentOfType(anchor, PyAugAssignmentStatement.class);
    final PyElement element = augAssignment != null ? augAssignment : anchor;
    try {
      final List<Instruction> defs = PyDefUseUtil.getLatestDefs(scopeOwner, name, element, true, false);
      // null means empty set of possible types, Ref(null) means Any
      final @Nullable Ref<PyType> combinedType = StreamEx.of(defs)
        .select(ReadWriteInstruction.class)
        .map(instr -> instr.getType(context, anchor))
        // don't use foldLeft(BiFunction) here, as it doesn't support null results
        .foldLeft(null, (accType, defType) -> {
          if (defType == null) {
            return accType;
          }
          else if (accType == null) {
            return defType;
          }
          else {
            return Ref.create(PyUnionType.union(accType.get(), defType.get()));
          }
        });
      return Ref.deref(combinedType);
    }
    catch (PyDefUseUtil.InstructionNotFoundException ignored) {
    }
    return null;
  }

  @Nullable
  public static PyType getReferenceTypeFromOverridingProviders(@NotNull PsiElement target,
                                                               @NotNull TypeEvalContext context,
                                                               @Nullable PsiElement anchor) {
    return StreamEx
      .of(Extensions.getExtensions(PyTypeProvider.EP_NAME))
      .select(PyOverridingTypeProvider.class)
      .map(provider -> provider.getReferenceType(target, context, anchor))
      .findFirst(Objects::nonNull)
      .orElse(null);
  }

  @Nullable
  public static PyType getReferenceTypeFromProviders(@NotNull PsiElement target,
                                                     @NotNull TypeEvalContext context,
                                                     @Nullable PsiElement anchor) {
    for (PyTypeProvider provider : Extensions.getExtensions(PyTypeProvider.EP_NAME)) {
      final PyType result = provider.getReferenceType(target, context, anchor);
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

  private static class MultiFollowQueueNode {

    @NotNull
    private final PyReferenceExpression myReferenceExpression;

    @NotNull
    private final List<PyExpression> myQualifiers;

    private MultiFollowQueueNode(@NotNull PyReferenceExpression referenceExpression, @NotNull List<PyExpression> qualifiers) {
      myReferenceExpression = referenceExpression;
      myQualifiers = qualifiers;
    }

    @NotNull
    public static MultiFollowQueueNode create(@Nullable MultiFollowQueueNode previous, @NotNull PyReferenceExpression referenceExpression) {
      final PyExpression qualifier = referenceExpression.getQualifier();
      final List<PyExpression> previousQualifiers = previous == null ? Collections.emptyList() : previous.myQualifiers;
      final List<PyExpression> newQualifiers = qualifier == null ? previousQualifiers : ContainerUtil.append(previousQualifiers, qualifier);

      return new MultiFollowQueueNode(referenceExpression, newQualifiers);
    }
  }
}

