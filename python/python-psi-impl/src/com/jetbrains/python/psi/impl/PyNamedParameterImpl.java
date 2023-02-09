// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.navigation.ItemPresentation;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.resolve.reference.ReferenceProvidersRegistry;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.stubs.IStubElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.ui.IconManager;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.Processor;
import com.jetbrains.python.PyElementTypes;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.PythonDialectsTokenSetProvider;
import com.jetbrains.python.codeInsight.controlflow.ScopeOwner;
import com.jetbrains.python.codeInsight.dataflow.scope.ScopeUtil;
import com.jetbrains.python.codeInsight.typing.PyTypingTypeProvider;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.stubs.PyNamedParameterStub;
import com.jetbrains.python.psi.types.*;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.*;
import java.util.stream.Collectors;


public class PyNamedParameterImpl extends PyBaseElementImpl<PyNamedParameterStub> implements PyNamedParameter, ContributedReferenceHost {
  public PyNamedParameterImpl(ASTNode astNode) {
    super(astNode);
  }

  public PyNamedParameterImpl(final PyNamedParameterStub stub) {
    this(stub, PyElementTypes.NAMED_PARAMETER);
  }

  public PyNamedParameterImpl(final PyNamedParameterStub stub, IStubElementType nodeType) {
    super(stub, nodeType);
  }

  @Override
  public final PsiReference @NotNull [] getReferences() {
    return ReferenceProvidersRegistry.getReferencesFromProviders(this, PsiReferenceService.Hints.NO_HINTS);
  }

  @Nullable
  @Override
  public String getName() {
    final PyNamedParameterStub stub = getStub();
    if (stub != null) {
      return stub.getName();
    }
    else {
      ASTNode node = getNameIdentifierNode();
      return node != null ? node.getText() : null;
    }
  }

  @Override
  public int getTextOffset() {
    ASTNode node = getNameIdentifierNode();
    return node == null ? super.getTextOffset() : node.getTextRange().getStartOffset();
  }

  @Nullable
  protected ASTNode getNameIdentifierNode() {
    return getNode().findChildByType(PyTokenTypes.IDENTIFIER);
  }

  @Override
  @Nullable
  public PsiElement getNameIdentifier() {
    final ASTNode node = getNameIdentifierNode();
    return node == null ? null : node.getPsi();
  }

  @Override
  @NotNull
  public PsiElement setName(@NotNull String name) throws IncorrectOperationException {
    final ASTNode oldNameIdentifier = getNameIdentifierNode();
    if (oldNameIdentifier != null) {
      final ASTNode nameElement = PyUtil.createNewName(this, name);
      getNode().replaceChild(oldNameIdentifier, nameElement);
    }
    return this;
  }

  @Override
  protected void acceptPyVisitor(PyElementVisitor pyVisitor) {
    pyVisitor.visitPyNamedParameter(this);
  }

  @Override
  public boolean isPositionalContainer() {
    final PyNamedParameterStub stub = getStub();
    if (stub != null) {
      return stub.isPositionalContainer();
    }
    else {
      return getNode().findChildByType(PyTokenTypes.MULT) != null;
    }
  }

  @Override
  public boolean isKeywordContainer() {
    final PyNamedParameterStub stub = getStub();
    if (stub != null) {
      return stub.isKeywordContainer();
    }
    else {
      return getNode().findChildByType(PyTokenTypes.EXP) != null;
    }
  }

  @Override
  public boolean isKeywordOnly() {
    final PyParameterList parameters = getStubOrPsiParentOfType(PyParameterList.class);
    if (parameters == null) {
      return false;
    }
    boolean varargSeen = false;
    for (PyParameter param : parameters.getParameters()) {
      if (param == this) {
        break;
      }
      final PyNamedParameter named = param.getAsNamed();
      if ((named != null && named.isPositionalContainer()) || param instanceof PySingleStarParameter) {
        varargSeen = true;
        break;
      }
    }
    return varargSeen;
  }

  @Override
  @Nullable
  public PyExpression getDefaultValue() {
    final PyNamedParameterStub stub = getStub();
    if (stub != null && stub.getDefaultValueText() == null) {
      return null;
    }
    final ASTNode[] nodes = getNode().getChildren(PythonDialectsTokenSetProvider.getInstance().getExpressionTokens());
    if (nodes.length > 0) {
      return (PyExpression)nodes[0].getPsi();
    }
    return null;
  }

  @Override
  public boolean hasDefaultValue() {
    final PyNamedParameterStub stub = getStub();
    if (stub != null) {
      return stub.getDefaultValueText() != null;
    }
    return getDefaultValue() != null;
  }

  @Nullable
  @Override
  public String getDefaultValueText() {
    final PyNamedParameterStub stub = getStub();
    if (stub != null) {
      return stub.getDefaultValueText();
    }
    return ParamHelper.getDefaultValueText(getDefaultValue());
  }

  @NotNull
  @Override
  public String getRepr(boolean includeDefaultValue, @Nullable TypeEvalContext context) {
    return PyCallableParameterImpl.psi(this).getPresentableText(includeDefaultValue, context);
  }

  @Override
  @Nullable
  public PyType getArgumentType(@NotNull TypeEvalContext context) {
    return PyCallableParameterImpl.psi(this).getArgumentType(context);
  }

  @Override
  public PyAnnotation getAnnotation() {
    return getStubOrPsiChild(PyElementTypes.ANNOTATION);
  }

  @Nullable
  @Override
  public String getAnnotationValue() {
    return getAnnotationContentFromStubOrPsi(this);
  }

  @Override
  @NotNull
  public Icon getIcon(final int flags) {
    return IconManager.getInstance().getPlatformIcon(com.intellij.ui.PlatformIcons.Parameter);
  }

  @Override
  @NotNull
  public PyNamedParameter getAsNamed() {
    return this;
  }

  @Override
  @Nullable
  public PyTupleParameter getAsTuple() {
    return null; // we're not a tuple
  }

  @Nullable
  protected PyFunction getContainingFunction(@NotNull PyParameterList parameterList) {
    return parameterList.getContainingFunction();
  }

  @Override
  @Nullable
  public PyType getType(@NotNull TypeEvalContext context, @NotNull TypeEvalContext.Key key) {
    final PsiElement parent = getParentByStub();
    if (parent instanceof PyParameterList) {
      PyFunction func = getContainingFunction((PyParameterList)parent);
      if (func != null) {
        for (PyTypeProvider provider : PyTypeProvider.EP_NAME.getExtensionList()) {
          final Ref<PyType> resultRef = provider.getParameterType(this, func, context);
          if (resultRef != null) {
            return resultRef.get();
          }
        }
        if (isSelf()) {
          // must be 'self' or 'cls'
          final PyClass containingClass = func.getContainingClass();
          if (containingClass != null) {
            final boolean isDefinition = PyUtil.isNewMethod(func) || func.getModifier() == PyFunction.Modifier.CLASSMETHOD;

            final PyCollectionType genericType = PyTypeChecker.findGenericDefinitionType(containingClass, context);
            if (genericType != null) {
              return isDefinition ? genericType.toClass() : genericType;
            }

            return new PyClassTypeImpl(containingClass, isDefinition);
          }
        }
        if (isKeywordContainer()) {
          return PyTypeUtil.toKeywordContainerType(this, null);
        }
        if (isPositionalContainer()) {
          return PyTypeUtil.toPositionalContainerType(this, null);
        }
        if (context.maySwitchToAST(this)) {
          final PyExpression defaultValue = getDefaultValue();
          if (defaultValue != null) {
            final PyType type = context.getType(defaultValue);
            if (type != null && !(type instanceof PyNoneType)) {
              if (type instanceof PyTupleType) {
                return PyUnionType.createWeakType(type);
              }
              return type;
            }
          }
        }
        // Guess the type from file-local calls
        if (context.allowCallContext(this)) {
          final List<PyType> types = new ArrayList<>();
          final PyResolveContext resolveContext = PyResolveContext.defaultContext(context);
          final PyCallableParameter parameter = PyCallableParameterImpl.psi(this);

          processLocalCalls(
            func, call -> {
              StreamEx
                .of(call.multiMapArguments(resolveContext))
                .flatCollection(mapping -> mapping.getMappedParameters().entrySet())
                .filter(entry -> parameter.equals(entry.getValue()))
                .map(Map.Entry::getKey)
                .nonNull()
                .map(context::getType)
                .nonNull()
                .forEach(types::add);
              return true;
            }
          );

          if (!types.isEmpty()) {
            return PyUnionType.createWeakType(PyUnionType.union(types));
          }
        }
        if (context.maySwitchToAST(this)) {
          final PyType typeFromUsages = getTypeFromUsages(context);
          if (typeFromUsages != null) {
            return typeFromUsages;
          }
        }
      }
    }
    return null;
  }

  @Override
  public ItemPresentation getPresentation() {
    return new PyElementPresentation(this);
  }

  @Nullable
  private PyType getTypeFromUsages(@NotNull TypeEvalContext context) {
    final Set<String> usedAttributes = new LinkedHashSet<>();

    final ScopeOwner owner = ScopeUtil.getScopeOwner(this);
    final String name = getName();

    final Ref<Boolean> parameterWasReassigned = Ref.create(false);
    final Ref<Boolean> noneComparison = Ref.create(false);

    if (owner != null && name != null) {
      owner.accept(new PyRecursiveElementVisitor() {
        @Override
        public void visitPyElement(@NotNull PyElement node) {
          if (parameterWasReassigned.get()) return;

          if (node instanceof ScopeOwner && node != owner) {
            return;
          }
          if (node instanceof PyQualifiedExpression expr) {
            final PyExpression qualifier = expr.getQualifier();
            if (qualifier != null) {
              final String attributeName = expr.getReferencedName();
              final PyExpression referencedExpr = node instanceof PyBinaryExpression && PyNames.isRightOperatorName(attributeName) ?
                                                  ((PyBinaryExpression)node).getRightExpression() : qualifier;
              if (attributeName != null && isReferenceToParameter(referencedExpr)) {
                usedAttributes.add(attributeName);
              }
            }
            else if (isReferenceToParameter(expr)) {
              StreamEx.of(getParametersByCallArgument(expr, context))
                      .nonNull()
                      .map(parameter -> parameter.getType(context))
                      .select(PyStructuralType.class)
                      .forEach(type -> usedAttributes.addAll(type.getAttributeNames()));
            }
          }
          super.visitPyElement(node);
        }

        @Override
        public void visitPyIfStatement(@NotNull PyIfStatement node) {
          if (parameterWasReassigned.get()) return;

          final PyExpression ifCondition = node.getIfPart().getCondition();
          if (ifCondition != null) {
            ifCondition.accept(this);
          }
          for (PyIfPart part : node.getElifParts()) {
            final PyExpression elseIfCondition = part.getCondition();
            if (elseIfCondition != null) {
              elseIfCondition.accept(this);
            }
          }
        }

        @Override
        public void visitPyCallExpression(@NotNull PyCallExpression node) {
          if (parameterWasReassigned.get()) return;

          Optional
            .ofNullable(node.getCallee())
            .filter(callee -> "len".equals(callee.getName()) && isReferenceToParameter(node.getArgument(0, PyReferenceExpression.class)))
            .map(PyExpression::getReference)
            .map(PsiReference::resolve)
            .filter(element -> PyBuiltinCache.getInstance(element).isBuiltin(element))
            .ifPresent(callable -> usedAttributes.add(PyNames.LEN));

          super.visitPyCallExpression(node);
        }

        @Override
        public void visitPyForStatement(@NotNull PyForStatement node) {
          if (parameterWasReassigned.get()) return;

          if (isReferenceToParameter(node.getForPart().getSource())) {
            usedAttributes.add(PyNames.ITER);
          }

          super.visitPyForStatement(node);
        }

        @Override
        public void visitPyTargetExpression(@NotNull PyTargetExpression node) {
          if (parameterWasReassigned.get()) return;

          if (isReferenceToParameter(node)) {
            parameterWasReassigned.set(true);
          }
          else {
            super.visitPyTargetExpression(node);
          }
        }

        @Override
        public void visitPyBinaryExpression(@NotNull PyBinaryExpression node) {
          super.visitPyBinaryExpression(node);

          if (noneComparison.get() || !node.isOperator(PyNames.IS) && !node.isOperator("isnot")) return;

          final PyExpression lhs = node.getLeftExpression();
          final PyExpression rhs = node.getRightExpression();

          if (isReferenceToParameter(lhs) ^ isReferenceToParameter(rhs) &&
              (lhs != null && context.getType(lhs) instanceof PyNoneType) ^ (rhs != null && context.getType(rhs) instanceof PyNoneType)) {
            noneComparison.set(true);
          }
        }

        @Contract("null -> false")
        private boolean isReferenceToParameter(@Nullable PsiElement element) {
          if (element == null) return false;
          final PsiReference reference = element.getReference();
          return reference != null && reference.isReferenceTo(PyNamedParameterImpl.this);
        }
      });
    }

    if (!usedAttributes.isEmpty()) {
      final PyStructuralType structuralType = new PyStructuralType(usedAttributes, true);
      return noneComparison.get() ? PyUnionType.union(structuralType, PyNoneType.INSTANCE) : structuralType;
    }

    return null;
  }

  @NotNull
  private List<PyCallableParameter> getParametersByCallArgument(@NotNull PsiElement element, @NotNull TypeEvalContext context) {
    final PyArgumentList argumentList = PsiTreeUtil.getParentOfType(element, PyArgumentList.class);
    if (argumentList != null) {
      boolean elementIsArgument = false;
      for (PyExpression argument : argumentList.getArgumentExpressions()) {
        if (PyPsiUtils.flattenParens(argument) == element) {
          elementIsArgument = true;
          break;
        }
      }
      final PyCallExpression callExpression = argumentList.getCallExpression();
      if (elementIsArgument && callExpression != null) {
        final PyExpression callee = callExpression.getCallee();
        if (callee instanceof PyReferenceExpression calleeReferenceExpr) {
          final PyExpression firstQualifier = PyPsiUtils.getFirstQualifier(calleeReferenceExpr);
          final PsiReference ref = firstQualifier.getReference();
          if (ref != null && ref.isReferenceTo(this)) {
            return Collections.emptyList();
          }
        }
        final PyResolveContext resolveContext = PyResolveContext.defaultContext(context);
        return callExpression.multiMapArguments(resolveContext)
          .stream()
          .flatMap(mapping -> mapping.getMappedParameters().entrySet().stream())
          .filter(entry -> entry.getKey() == element)
          .map(Map.Entry::getValue)
          .collect(Collectors.toList());
      }
    }
    return Collections.emptyList();
  }

  private static void processLocalCalls(@NotNull PyFunction function, @NotNull Processor<? super PyCallExpression> processor) {
    final PsiFile file = function.getContainingFile();
    final String name = function.getName();
    if (file != null && name != null) {
      // Text search is faster than ReferencesSearch in LocalSearchScope
      final String text = file.getText();
      for (int pos = text.indexOf(name); pos != -1; pos = text.indexOf(name, pos + 1)) {
        final PsiReference ref = file.findReferenceAt(pos);
        if (ref != null && ref.isReferenceTo(function)) {
          final PyCallExpression expr = PsiTreeUtil.getParentOfType(file.findElementAt(pos), PyCallExpression.class);
          if (expr != null && !processor.process(expr)) {
            return;
          }
        }
      }
    }
  }

  @Override
  public String toString() {
    return super.toString() + "('" + getName() + "')";
  }

  @NotNull
  @Override
  public SearchScope getUseScope() {
    final ScopeOwner owner = ScopeUtil.getScopeOwner(this);
    if (owner instanceof PyFunction) {
      return owner.getUseScope();
    }
    return new LocalSearchScope(getContainingFile());
  }

  @Override
  public boolean isSelf() {
    if (isPositionalContainer() || isKeywordContainer()) {
      return false;
    }
    PyFunction function = getStubOrPsiParentOfType(PyFunction.class);
    if (function == null) {
      return false;
    }
    final PyClass cls = function.getContainingClass();
    final PyParameter[] parameters = function.getParameterList().getParameters();
    if (cls != null && parameters.length > 0 && parameters[0] == this) {
      if (PyUtil.isNewMethod(function)) {
        return true;
      }
      final PyFunction.Modifier modifier = function.getModifier();
      if (modifier != PyFunction.Modifier.STATICMETHOD) {
        return true;
      }
    }
    return false;
  }

  @Nullable
  @Override
  public PsiComment getTypeComment() {
    for (PsiElement next = getNextSibling(); next != null; next = next.getNextSibling()) {
      if (next.textContains('\n')) break;
      if (!(next instanceof PsiWhiteSpace)) {
        if (",".equals(next.getText())) continue;
        if (next instanceof PsiComment && PyTypingTypeProvider.getTypeCommentValue(next.getText()) != null) {
          return (PsiComment)next;
        }
        break;
      }
    }
    return null;
  }

  @Nullable
  @Override
  public String getTypeCommentAnnotation() {
    return getTypeCommentAnnotationFromStubOrPsi(this);
  }
}
