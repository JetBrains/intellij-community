// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiPolyVariantReference;
import com.intellij.psi.PsiReference;
import com.intellij.psi.util.QualifiedName;
import com.jetbrains.python.PyElementTypes;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.PythonDialectsTokenSetProvider;
import com.jetbrains.python.codeInsight.typing.PyTypingTypeProvider;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.references.PyOperatorReference;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.types.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author yole
 */
public class PyPrefixExpressionImpl extends PyElementImpl implements PyPrefixExpression {
  public PyPrefixExpressionImpl(ASTNode astNode) {
    super(astNode);
  }

  @Override
  public PyExpression getOperand() {
    return (PyExpression)childToPsi(PythonDialectsTokenSetProvider.INSTANCE.getExpressionTokens(), 0);
  }

  @Nullable
  public PsiElement getPsiOperator() {
    final ASTNode node = getNode();
    final ASTNode child = node.findChildByType(PyElementTypes.UNARY_OPS);
    return child != null ? child.getPsi() : null;
  }

  @NotNull
  @Override
  public PyElementType getOperator() {
    final PsiElement op = getPsiOperator();
    assert op != null;
    return (PyElementType)op.getNode().getElementType();
  }

  @Override
  protected void acceptPyVisitor(PyElementVisitor pyVisitor) {
    pyVisitor.visitPyPrefixExpression(this);
  }

  @Override
  public PsiReference getReference() {
    return getReference(PyResolveContext.noImplicits());
  }

  @NotNull
  @Override
  public PsiPolyVariantReference getReference(@NotNull PyResolveContext context) {
    return new PyOperatorReference(this, context);
  }

  @Override
  public PyType getType(@NotNull TypeEvalContext context, @NotNull TypeEvalContext.Key key) {
    if (getOperator() == PyTokenTypes.NOT_KEYWORD) {
      return PyBuiltinCache.getInstance(this).getBoolType();
    }
    final boolean isAwait = getOperator() == PyTokenTypes.AWAIT_KEYWORD;
    if (isAwait) {
      final PyExpression operand = getOperand();
      if (operand != null) {
        final PyType operandType = context.getType(operand);
        final Ref<PyType> type = getGeneratorReturnType(operandType);
        if (type != null) {
          return type.get();
        }
      }
    }
    final PsiReference ref = getReference(PyResolveContext.noImplicits().withTypeEvalContext(context));
    final PsiElement resolved = ref.resolve();
    if (resolved instanceof PyCallable) {
      // TODO: Make PyPrefixExpression a PyCallSiteExpression, use getCallType() here and analyze it in PyTypeChecker.analyzeCallSite()
      final PyType returnType = ((PyCallable)resolved).getReturnType(context, key);
      return isAwait ? Ref.deref(getGeneratorReturnType(returnType)) : returnType;
    }
    return null;
  }

  @Override
  public PyExpression getQualifier() {
    return getOperand();
  }

  @Nullable
  @Override
  public QualifiedName asQualifiedName() {
    return PyPsiUtils.asQualifiedName(this);
  }

  @Override
  public boolean isQualified() {
    return getQualifier() != null;
  }

  @Override
  public String getReferencedName() {
    final PyElementType t = getOperator();
    if (t == PyTokenTypes.PLUS) {
      return PyNames.POS;
    }
    else if (t == PyTokenTypes.MINUS) {
      return PyNames.NEG;
    }
    return getOperator().getSpecialMethodName();
  }

  @Override
  public ASTNode getNameElement() {
    final PsiElement op = getPsiOperator();
    return op != null ? op.getNode() : null;
  }

  @Nullable
  @Override
  public PyExpression getReceiver(@Nullable PyCallable resolvedCallee) {
    return getOperand();
  }

  @NotNull
  @Override
  public List<PyExpression> getArguments(@Nullable PyCallable resolvedCallee) {
    return Collections.emptyList();
  }

  @Nullable
  private static Ref<PyType> getGeneratorReturnType(@Nullable PyType type) {
    if (type instanceof PyClassLikeType && type instanceof PyCollectionType) {
      if (type instanceof PyClassType && PyNames.AWAITABLE.equals(((PyClassType)type).getPyClass().getName())) {
        return Ref.create(((PyCollectionType)type).getIteratedItemType());
      }
      else {
        return PyTypingTypeProvider.coroutineOrGeneratorElementType(type);
      }
    }
    else if (type instanceof PyUnionType) {
      final List<PyType> memberReturnTypes = new ArrayList<>();
      final PyUnionType unionType = (PyUnionType)type;
      for (PyType member : unionType.getMembers()) {
        memberReturnTypes.add(Ref.deref(getGeneratorReturnType(member)));
      }
      return Ref.create(PyUnionType.union(memberReturnTypes));
    }
    return null;
  }
}
