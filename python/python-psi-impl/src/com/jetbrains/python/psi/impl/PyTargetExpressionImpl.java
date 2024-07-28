// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.navigation.ItemPresentation;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiPolyVariantReference;
import com.intellij.psi.PsiReference;
import com.intellij.psi.ResolveResult;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.stubs.IStubElementType;
import com.intellij.psi.stubs.StubElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.QualifiedName;
import com.intellij.ui.IconManager;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.PyStubElementTypes;
import com.jetbrains.python.codeInsight.controlflow.ControlFlowCache;
import com.jetbrains.python.codeInsight.controlflow.ScopeOwner;
import com.jetbrains.python.codeInsight.dataflow.scope.Scope;
import com.jetbrains.python.codeInsight.dataflow.scope.ScopeUtil;
import com.jetbrains.python.codeInsight.typing.PyTypingTypeProvider;
import com.jetbrains.python.documentation.docstrings.DocStringUtil;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.references.PyQualifiedReference;
import com.jetbrains.python.psi.impl.references.PyTargetReference;
import com.jetbrains.python.psi.impl.stubs.CustomTargetExpressionStub;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.resolve.PyResolveUtil;
import com.jetbrains.python.psi.resolve.QualifiedNameFinder;
import com.jetbrains.python.psi.resolve.RatedResolveResult;
import com.jetbrains.python.psi.stubs.PyAnnotationOwnerStub;
import com.jetbrains.python.psi.stubs.PyClassStub;
import com.jetbrains.python.psi.stubs.PyFunctionStub;
import com.jetbrains.python.psi.stubs.PyTargetExpressionStub;
import com.jetbrains.python.psi.types.*;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;


public class PyTargetExpressionImpl extends PyBaseElementImpl<PyTargetExpressionStub> implements PyTargetExpression {
  @Nullable private volatile QualifiedName myQualifiedName;

  public PyTargetExpressionImpl(ASTNode astNode) {
    super(astNode);
  }

  public PyTargetExpressionImpl(final PyTargetExpressionStub stub) {
    super(stub, PyStubElementTypes.TARGET_EXPRESSION);
  }

  public PyTargetExpressionImpl(final PyTargetExpressionStub stub, IStubElementType nodeType) {
    super(stub, nodeType);
  }

  @Override
  protected void acceptPyVisitor(PyElementVisitor pyVisitor) {
    pyVisitor.visitPyTargetExpression(this);
  }

  @Override
  public int getTextOffset() {
    return PyTargetExpression.super.getTextOffset();
  }

  @Nullable
  @Override
  public String getName() {
    final PyTargetExpressionStub stub = getStub();
    if (stub != null) {
      return stub.getName();
    }
    return PyTargetExpression.super.getName();
  }

  @Override
  public PsiElement setName(@NotNull String name) throws IncorrectOperationException {
    final ASTNode oldNameElement = getNameElement();
    if (oldNameElement != null) {
      final ASTNode nameElement = PyUtil.createNewName(this, name);
      getNode().replaceChild(oldNameElement, nameElement);
    }
    return this;
  }

  @Override
  public PyType getType(@NotNull TypeEvalContext context, @NotNull TypeEvalContext.Key key) {
    if (PyNames.ALL.equals(getName())) {
      // no type for __all__, to avoid unresolved reference errors for expressions where a qualifier is a name
      // imported via __all__
      return null;
    }
    final Ref<PyType> pyType = PyReferenceExpressionImpl.getReferenceTypeFromProviders(this, context, null);
    if (pyType != null) {
      return pyType.get();
    }
    PyType type = getTypeFromDocString();
    if (type != null) {
      return type;
    }
    if (!context.maySwitchToAST(this)) {
      final PyResolveContext resolveContext = PyResolveContext.defaultContext(context);

      final List<PyType> types = StreamEx
        .of(multiResolveAssignedValue(resolveContext))
        .select(PyTypedElement.class)
        .map(context::getType)
        .toList();

      return PyUnionType.union(types);
    }
    type = getTypeFromComment(this);
    if (type != null) {
      return type;
    }
    final PsiElement parent = PsiTreeUtil.skipParentsOfType(this, PyParenthesizedExpression.class);
    if (parent instanceof PyAssignmentStatement assignmentStatement) {
      PyExpression assignedValue = assignmentStatement.getAssignedValue();
      if (assignedValue instanceof PyParenthesizedExpression) {
        assignedValue = ((PyParenthesizedExpression)assignedValue).getContainedExpression();
      }
      if (assignedValue != null) {
        if (assignedValue instanceof PyYieldExpression assignedYield) {
          return assignedYield.isDelegating() ? context.getType(assignedValue) : null;
        }
        return context.getType(assignedValue);
      }
    }
    if (parent instanceof PyTupleExpression || parent instanceof PyListLiteralExpression) {
      PsiElement nextParent =
        PsiTreeUtil.skipParentsOfType(parent, PyParenthesizedExpression.class, PyTupleExpression.class, PyListLiteralExpression.class);
      if (nextParent instanceof PyAssignmentStatement assignment) {
        final PyExpression value = assignment.getAssignedValue();
        final PyExpression lhs = assignment.getLeftHandSideExpression();
        final PySequenceExpression targetTupleOrList = PsiTreeUtil.findChildOfType(lhs, PySequenceExpression.class, false);
        if (value != null && (targetTupleOrList instanceof PyTupleExpression || targetTupleOrList instanceof PyListLiteralExpression)) {
          final PyType positionalItemType = getTargetTypeFromIterableUnpacking(targetTupleOrList, value, context);
          if (positionalItemType != null) {
            return positionalItemType;
          }
        }
      }
    }
    if (parent instanceof PyWithItem) {
      return getWithItemVariableType((PyWithItem)parent, context);
    }
    if (parent instanceof PyAssignmentExpression) {
      final PyExpression assignedValue = ((PyAssignmentExpression)parent).getAssignedValue();
      return assignedValue == null ? null : context.getType(assignedValue);
    }
    if (parent instanceof PyGlobalStatement || parent instanceof PyNonlocalStatement) {
      PyResolveContext resolveContext = PyResolveContext.defaultContext(context);
      List<PyType> collect = StreamEx.of(getReference(resolveContext).multiResolve(false))
        .map(ResolveResult::getElement)
        .select(PyTypedElement.class)
        .map(context::getType)
        .toList();

      return PyUnionType.union(collect);
    }
    if (parent instanceof PyExceptPart && ((PyExceptPart)parent).isStar() &&
        LanguageLevel.forElement(this).isAtLeast(LanguageLevel.PYTHON311)) {
      return PyClassTypeImpl.createTypeByQName(this, "ExceptionGroup", false);
    }
    PyType iterType = getTypeFromIteration(context);
    if (iterType != null) {
      return iterType;
    }
    PyType excType = getTypeFromExcept();
    if (excType != null) {
      return excType;
    }
    return null;
  }

  private @Nullable PyType getTargetTypeFromIterableUnpacking(@NotNull PySequenceExpression topmostContainingTupleOrList,
                                                              @NotNull PyExpression assignedIterable,
                                                              @NotNull TypeEvalContext context) {
    PyType assignedType = PyUnionType.toNonWeakType(context.getType(assignedIterable));
    if (assignedType instanceof PyTupleType tupleType) {
      return PyTypeChecker.getTargetTypeFromTupleAssignment(this, topmostContainingTupleOrList, tupleType);
    }
    else if (assignedType instanceof PyClassLikeType classLikeType) {
      PyNamedTupleType namedTupleType = ContainerUtil.findInstance(classLikeType.getAncestorTypes(context), PyNamedTupleType.class);
      if (namedTupleType != null) {
        return PyTypeChecker.getTargetTypeFromTupleAssignment(this, topmostContainingTupleOrList, namedTupleType);
      }
      else {
        return getIterationType(assignedType, assignedIterable, assignedIterable, context);
      }
    }
    return null;
  }

  @Nullable
  @Override
  public String getAnnotationValue() {
    final PyAnnotationOwnerStub stub = getStub();
    if (stub != null) {
      return stub.getAnnotation();
    }
    return PyTargetExpression.super.getAnnotationValue();
  }

  @Nullable
  private static PyType getWithItemVariableType(@NotNull PyWithItem item, @NotNull TypeEvalContext context) {
    final PyExpression withExpression = item.getExpression();
    final PyType withType = context.getType(withExpression);
    final PyWithStatement withStatement = PsiTreeUtil.getParentOfType(item, PyWithStatement.class);
    final boolean isAsync = withStatement != null && withStatement.isAsync();

    return PyTypeUtil
      .toStream(withType)
      .select(PyClassType.class)
      .map(t -> getEnterTypeFromPyClass(withExpression, t, isAsync, context))
      .collect(PyTypeUtil.toUnion());
  }

  @Nullable
  private static PyType getEnterTypeFromPyClass(@NotNull PyExpression withExpression,
                                                @NotNull PyClassType withType,
                                                boolean isAsync,
                                                @NotNull TypeEvalContext context) {
    final PyClass cls = withType.getPyClass();
    final PyFunction enter = cls.findMethodByName(isAsync ? PyNames.AENTER : PyNames.ENTER, true, context);
    if (enter != null) {
      final PyType enterType = getContextSensitiveType(enter, context, withExpression);
      if (enterType != null) {
        return isAsync ? Ref.deref(PyTypingTypeProvider.coroutineOrGeneratorElementType(enterType)) : enterType;
      }
      for (PyTypeProvider provider : PyTypeProvider.EP_NAME.getExtensionList()) {
        final PyType typeFromProvider = provider.getContextManagerVariableType(cls, withExpression, context);
        if (typeFromProvider != null) {
          return typeFromProvider;
        }
      }
      // Guess the return type of __enter__
      return PyUnionType.createWeakType(withType);
    }
    return null;
  }

  @Nullable
  public PyType getTypeFromDocString() {
    String typeName = null;
    final String name = getName();
    final StructuredDocString targetDocString = getStructuredDocString();
    if (targetDocString != null) {
      typeName = targetDocString.getParamType(null);
      if (typeName == null) {
        typeName = targetDocString.getParamType(name);
      }
    }
    if (typeName == null && PyUtil.isAttribute(this)) {
      final PyClass cls = getContainingClass();
      if (cls != null) {
        final StructuredDocString classDocString = cls.getStructuredDocString();
        if (classDocString != null) {
          typeName = classDocString.getParamType(name);
        }
      }
    }
    if (typeName != null) {
      return PyTypeParser.getTypeByName(this, typeName);
    }
    return null;
  }

  @Nullable
  public static PyType getTypeFromComment(PyTargetExpressionImpl targetExpression) {
    String docComment = DocStringUtil.getAttributeDocComment(targetExpression);
    if (docComment != null) {
      StructuredDocString structuredDocString = DocStringUtil.parse(docComment, targetExpression);
      String typeName = structuredDocString.getParamType(null);
      if (typeName == null) {
        typeName = structuredDocString.getParamType(targetExpression.getName());
      }
      if (typeName != null) {
        return PyTypeParser.getTypeByName(targetExpression, typeName);
      }
    }
    return null;
  }

  @Nullable
  private PyType getTypeFromIteration(@NotNull TypeEvalContext context) {
    PyExpression target = null;
    PyExpression source = null;
    final PyForPart forPart = PsiTreeUtil.getParentOfType(this, PyForPart.class);
    if (forPart != null) {
      final PyExpression expr = forPart.getTarget();
      if (PsiTreeUtil.isAncestor(expr, this, false)) {
        target = expr;
        source = forPart.getSource();
      }
    }
    final PyComprehensionElement comprh = PsiTreeUtil.getParentOfType(this, PyComprehensionElement.class);
    if (comprh != null) {
      for (PyComprehensionForComponent c : comprh.getForComponents()) {
        final PyExpression expr = c.getIteratorVariable();
        if (PsiTreeUtil.isAncestor(expr, this, false)) {
          target = expr;
          source = c.getIteratedList();
        }
      }
    }
    if (source != null) {
      final PyType sourceType = context.getType(source);
      final PyType type = getIterationType(sourceType, source, this, context);
      target = PyPsiUtils.flattenParens(target);
      if (type instanceof PyTupleType tupleType && (target instanceof PyTupleExpression || target instanceof PyListLiteralExpression)) {
        return PyTypeChecker.getTargetTypeFromTupleAssignment(this, (PySequenceExpression)target, tupleType);
      }
      if (target == this && type != null) {
        return type;
      }
    }
    return null;
  }

  @Nullable
  public static PyType getIterationType(@Nullable PyType iterableType, @Nullable PyExpression source, @NotNull PsiElement anchor,
                                        @NotNull TypeEvalContext context) {
    if (iterableType instanceof PyTupleType tupleType) {
      return tupleType.getIteratedItemType();
    }
    else if (iterableType instanceof PyUnionType) {
      return ((PyUnionType)iterableType).map(member -> getIterationType(member, source, anchor, context));
    }
    else if (iterableType != null && PyABCUtil.isSubtype(iterableType, PyNames.ITERABLE, context)) {
      final PyFunction iterateMethod = findMethodByName(iterableType, PyNames.ITER, context);
      if (iterateMethod != null) {
        final PyType iterateReturnType = getContextSensitiveType(iterateMethod, context, source);
        return getIteratedItemType(iterateReturnType, source, anchor, context, false);
      }
      final Ref<PyType> nextMethodCallType = getNextMethodCallType(iterableType, source, anchor, context, false);
      if (nextMethodCallType != null) {
        return nextMethodCallType.get();
      }
      final PyFunction getItem = findMethodByName(iterableType, PyNames.GETITEM, context);
      if (getItem != null) {
        return getContextSensitiveType(getItem, context, source);
      }
    }
    else if (iterableType != null && PyABCUtil.isSubtype(iterableType, PyNames.ASYNC_ITERABLE, context)) {
      final PyFunction iterateMethod = findMethodByName(iterableType, PyNames.AITER, context);
      if (iterateMethod != null) {
        final PyType iterateReturnType = getContextSensitiveType(iterateMethod, context, source);
        return getIteratedItemType(iterateReturnType, source, anchor, context, true);
      }
    }
    return null;
  }

  @Nullable
  private static PyType getIteratedItemType(@Nullable PyType type,
                                            @Nullable PyExpression source,
                                            @NotNull PsiElement anchor,
                                            @NotNull TypeEvalContext context,
                                            boolean async) {
    final Ref<PyType> nextMethodCallType = getNextMethodCallType(type, source, anchor, context, async);
    if (nextMethodCallType != null && !nextMethodCallType.isNull()) {
      if (nextMethodCallType.get() instanceof PyCollectionType collectionType) {
        if (async && "typing.Awaitable".equals(collectionType.getClassQName())) {
          return collectionType.getIteratedItemType();
        }
      }
      return nextMethodCallType.get();
    }
    if (type instanceof PyCollectionType) {
      return ((PyCollectionType)type).getIteratedItemType();
    }
    return null;
  }

  @Nullable
  private static Ref<PyType> getNextMethodCallType(@Nullable PyType type,
                                                   @Nullable PyExpression source,
                                                   @NotNull PsiElement anchor,
                                                   @NotNull TypeEvalContext context,
                                                   boolean async) {
    if (type == null) return null;

    final String nextMethodName = async
                                  ? PyNames.ANEXT
                                  : !LanguageLevel.forElement(anchor).isPython2()
                                    ? PyNames.DUNDER_NEXT
                                    : PyNames.NEXT;
    final PyFunction next = findMethodByName(type, nextMethodName, context);
    if (next != null) {
      return Ref.create(getContextSensitiveType(next, context, source));
    }
    return null;
  }

  @Nullable
  private static PyFunction findMethodByName(@NotNull PyType type, @NotNull String name, @NotNull TypeEvalContext context) {
    final PyResolveContext resolveContext = PyResolveContext.defaultContext(context);
    final List<? extends RatedResolveResult> results = type.resolveMember(name, null, AccessDirection.READ, resolveContext);
    if (results != null) {
      List<PyFunction> allMethods = StreamEx.of(results)
        .map(RatedResolveResult::getElement)
        .select(PyFunction.class)
        .toList();
      // TODO Migrate this ad-hoc logic to the normal process of resolving overloads in PyCallExpressionHelper
      PyFunction matchingBySelf = ContainerUtil.find(allMethods, method -> selfParameterMatchesReceiver(method, type, context));
      return matchingBySelf != null ? matchingBySelf : ContainerUtil.getFirstItem(allMethods);
    }
    return null;
  }

  private static boolean selfParameterMatchesReceiver(@NotNull PyFunction method,
                                                      @NotNull PyType receiverType,
                                                      @NotNull TypeEvalContext context) {
    List<PyCallableParameter> parameters = method.getParameters(context);
    if (parameters.isEmpty()) return false;
    PyCallableParameter firstParameter = parameters.get(0);
    if (!firstParameter.isSelf()) return false;
    PyType selfParameterType = firstParameter.getType(context);
    return PyTypeChecker.match(selfParameterType, receiverType, context);
  }

  @Nullable
  public static PyType getContextSensitiveType(@NotNull PyFunction function, @NotNull TypeEvalContext context,
                                               @Nullable PyExpression source) {
    return function.getCallType(source, buildArgumentsToParametersMap(source, function, context), context);
  }

  @NotNull
  private static Map<PyExpression, PyCallableParameter> buildArgumentsToParametersMap(@Nullable PyExpression receiver,
                                                                                      @NotNull PyCallable callable,
                                                                                      @NotNull TypeEvalContext context) {
    if (receiver == null) return Collections.emptyMap();

    final PyCallableParameter firstParameter = ContainerUtil.getFirstItem(callable.getParameters(context));
    if (firstParameter == null || !firstParameter.isSelf()) return Collections.emptyMap();

    return Map.of(receiver, firstParameter);
  }

  @Nullable
  private PyType getTypeFromExcept() {
    PyExceptPart exceptPart = PsiTreeUtil.getParentOfType(this, PyExceptPart.class);
    if (exceptPart == null || exceptPart.getTarget() != this) {
      return null;
    }
    final PyExpression exceptClass = exceptPart.getExceptClass();
    if (exceptClass instanceof PyReferenceExpression) {
      final PsiElement element = ((PyReferenceExpression)exceptClass).getReference().resolve();
      if (element instanceof PyClass) {
        return new PyClassTypeImpl((PyClass)element, false);
      }
    }
    return null;
  }

  @Nullable
  @Override
  public QualifiedName asQualifiedName() {
    if (myQualifiedName == null) {
      myQualifiedName = PyPsiUtils.asQualifiedName(this);
    }
    return myQualifiedName;
  }

  @Override
  public String toString() {
    return super.toString() + ": " + getName();
  }

  @Override
  public Icon getIcon(final int flags) {
    if (isQualified() || PsiTreeUtil.getStubOrPsiParentOfType(this, PyDocStringOwner.class) instanceof PyClass) {
      return IconManager.getInstance().getPlatformIcon(com.intellij.ui.PlatformIcons.Field);
    }
    return IconManager.getInstance().getPlatformIcon(com.intellij.ui.PlatformIcons.Variable);
  }

  @Override
  public boolean isQualified() {
    PyTargetExpressionStub stub = getStub();
    if (stub != null) {
      return stub.isQualified();
    }
    return getQualifier() != null;
  }

  @NotNull
  @Override
  public List<PsiElement> multiResolveAssignedValue(@NotNull PyResolveContext resolveContext) {
    final TypeEvalContext context = resolveContext.getTypeEvalContext();

    if (context.maySwitchToAST(this)) {
      final PyExpression value = findAssignedValue();
      return value != null
             ? ContainerUtil.filter(PyUtil.multiResolveTopPriority(value, resolveContext), Objects::nonNull)
             : Collections.emptyList();
    }
    else {
      final QualifiedName qName = getAssignedQName();

      if (qName != null && qName.getComponentCount() != 0) {
        final ScopeOwner owner = ScopeUtil.getScopeOwner(this);
        if (owner != null) {
          return PyResolveUtil.resolveQualifiedNameInScope(qName, owner, context);
        }
      }

      return Collections.emptyList();
    }
  }

  @Nullable
  @Override
  public QualifiedName getAssignedQName() {
    return Ref.deref(getAssignedReferenceQualifiedName(this));
  }

  @Override
  public QualifiedName getCalleeName() {
    return Ref.deref(getAssignedCallCalleeQualifiedName(this));
  }

  @NotNull
  @Override
  public PsiReference getReference() {
    return getReference(PyResolveContext.defaultContext(TypeEvalContext.codeInsightFallback(getProject())));
  }

  @NotNull
  @Override
  public PsiPolyVariantReference getReference(@NotNull final PyResolveContext resolveContext) {
    if (isQualified()) {
      return new PyQualifiedReference(this, resolveContext);
    }
    return new PyTargetReference(this, resolveContext);
  }

  @NotNull
  @Override
  public SearchScope getUseScope() {
    if (isQualified()) {
      return super.getUseScope();
    }
    final ScopeOwner owner = ScopeUtil.getScopeOwner(this);
    if (owner != null) {
      final Scope scope = ControlFlowCache.getScope(owner);
      if (scope.isGlobal(getName())) {
        return GlobalSearchScope.projectScope(getProject());
      }
      if (scope.isNonlocal(getName())) {
        return new LocalSearchScope(getContainingFile());
      }
    }

    // find highest level function containing our var
    PyElement container = this;
    while (true) {
      PyElement parentContainer = PsiTreeUtil.getParentOfType(container, PyFunction.class, PyClass.class);
      if (parentContainer instanceof PyClass) {
        if (isQualified()) {
          return super.getUseScope();
        }
        break;
      }
      if (parentContainer == null) {
        break;
      }
      container = parentContainer;
    }
    if (container instanceof PyFunction) {
      return new LocalSearchScope(container);
    }
    return super.getUseScope();
  }

  @Override
  public PyClass getContainingClass() {
    final PyTargetExpressionStub stub = getStub();
    if (stub != null) {
      final StubElement parentStub = stub.getParentStub();
      if (parentStub instanceof PyClassStub) {
        return ((PyClassStub)parentStub).getPsi();
      }
      if (parentStub instanceof PyFunctionStub) {
        final StubElement functionParent = parentStub.getParentStub();
        if (functionParent instanceof PyClassStub) {
          return ((PyClassStub)functionParent).getPsi();
        }
      }

      return null;
    }

    return PyTargetExpression.super.getContainingClass();
  }

  @Override
  public ItemPresentation getPresentation() {
    return new PyElementPresentation(this);
  }

  @Nullable
  @Override
  public String getDocStringValue() {
    final PyTargetExpressionStub stub = getStub();
    if (stub != null) {
      return stub.getDocString();
    }
    return PyTargetExpression.super.getDocStringValue();
  }

  @Nullable
  @Override
  public StructuredDocString getStructuredDocString() {
    return DocStringUtil.getStructuredDocString(this);
  }

  @Override
  public void subtreeChanged() {
    super.subtreeChanged();
    myQualifiedName = null;
  }

  @Nullable
  @Override
  public String getQualifiedName() {
    return QualifiedNameFinder.getQualifiedName(this);
  }

  @Nullable
  @Override
  public String getTypeCommentAnnotation() {
    return getTypeCommentAnnotationFromStubOrPsi(this);
  }

  @Override
  public boolean hasAssignedValue() {
    final PyTargetExpressionStub stub = getStub();
    if (stub != null) {
      return stub.hasAssignedValue();
    }
    return findAssignedValue() != null;
  }

  @Nullable
  public static Ref<QualifiedName> getAssignedReferenceQualifiedName(@NotNull PyTargetExpression psi) {
    final PyTargetExpressionStub stub = psi.getStub();
    if (stub != null) {
      if (stub.getInitializerType() == PyTargetExpressionStub.InitializerType.ReferenceExpression) {
        return Ref.create(stub.getInitializer());
      }
      return null;
    }
    final PyExpression value = psi.findAssignedValue();
    return value instanceof PyReferenceExpression ? Ref.create(((PyReferenceExpression)value).asQualifiedName()) : null;
  }

  @Nullable
  public static Ref<QualifiedName> getAssignedCallCalleeQualifiedName(@NotNull PyTargetExpression psi) {
    final PyTargetExpressionStub stub = psi.getStub();
    if (stub != null) {
      final PyTargetExpressionStub.InitializerType initializerType = stub.getInitializerType();
      if (initializerType == PyTargetExpressionStub.InitializerType.CallExpression) {
        return Ref.create(stub.getInitializer());
      }
      else if (initializerType == PyTargetExpressionStub.InitializerType.Custom) {
        final CustomTargetExpressionStub customStub = stub.getCustomStub(CustomTargetExpressionStub.class);
        if (customStub != null) {
          final QualifiedName calleeName = customStub.getCalleeName();
          return calleeName != null ? Ref.create(calleeName) : null;
        }
      }
      return null;
    }
    final PyExpression value = psi.findAssignedValue();
    return value instanceof PyCallExpression ? Ref.create(PyPsiUtils.asQualifiedName(((PyCallExpression)value).getCallee())) : null;
  }
}
