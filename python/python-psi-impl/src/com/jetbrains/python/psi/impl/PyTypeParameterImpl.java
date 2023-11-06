package com.jetbrains.python.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.stubs.IStubElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import com.jetbrains.python.PyElementTypes;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.codeInsight.typing.PyTypingTypeProvider;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.stubs.PyTypeParameterElementType;
import com.jetbrains.python.psi.stubs.PyTypeParameterStub;
import com.jetbrains.python.psi.types.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class PyTypeParameterImpl extends PyBaseElementImpl<PyTypeParameterStub> implements PyTypeParameter {

  public PyTypeParameterImpl(ASTNode astNode) {
    super(astNode);
  }

  public PyTypeParameterImpl(PyTypeParameterStub stub) {
    this(stub, PyElementTypes.TYPE_PARAMETER);
  }

  public PyTypeParameterImpl(PyTypeParameterStub stub, IStubElementType nodeType) {
    super(stub, nodeType);
  }

  @Override
  protected void acceptPyVisitor(PyElementVisitor pyVisitor) {
    pyVisitor.visitPyTypeParameter(this);
  }

  @Override
  @Nullable
  public String getName() {
    PyTypeParameterStub stub = getStub();

    if (stub != null) {
      return stub.getName();
    }
    else {
      PsiElement identifier = getNameIdentifier();
      return identifier != null ? identifier.getText() : null;
    }
  }

  @Override
  @Nullable
  public PyExpression getBoundExpression() {
    return PsiTreeUtil.getChildOfType(this, PyExpression.class);
  }

  @Override
  @Nullable
  public String getBoundExpressionText() {
    PyTypeParameterStub stub = getStub();
    if (stub != null) {
      return stub.getBoundExpressionText();
    }

    PyExpression boundExpression = getBoundExpression();
    if (boundExpression != null) {
      return boundExpression.getText();
    }

    return null;
  }

  @Override
  @NotNull
  public PyTypeParameter.Kind getKind() {
    PyTypeParameterStub stub = getStub();

    if (stub != null) {
      return stub.getKind();
    }
    else {
      return PyTypeParameterElementType.getTypeParameterKindFromPsi(this);
    }
  }

  @Override
  @Nullable
  public PsiElement getNameIdentifier() {
    ASTNode nameNode = getNode().findChildByType(PyTokenTypes.IDENTIFIER);
    return nameNode != null ? nameNode.getPsi() : null;
  }

  @Override
  public PsiElement setName(@NotNull String name) throws IncorrectOperationException {
    PsiElement identifier = getNameIdentifier();
    if (identifier != null) {
      ASTNode newName = PyUtil.createNewName(this, name);
      ASTNode nameNode = identifier.getNode();

      getNode().replaceChild(nameNode, newName);
    }
    return this;
  }

  @Override
  @Nullable
  public PyType getType(@NotNull TypeEvalContext context, TypeEvalContext.@NotNull Key key) {
    return null;
  }
}
