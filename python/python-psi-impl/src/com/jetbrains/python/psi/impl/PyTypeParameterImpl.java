package com.jetbrains.python.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.stubs.IStubElementType;
import com.intellij.util.IncorrectOperationException;
import com.jetbrains.python.PyElementTypes;
import com.jetbrains.python.codeInsight.typing.PyTypingTypeProvider;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.stubs.PyTypeParameterStub;
import com.jetbrains.python.psi.types.PyClassTypeImpl;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
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
      return PyTypeParameter.super.getName();
    }
  }

  @Override
  @Nullable
  public String getBoundExpressionText() {
    PyTypeParameterStub stub = getStub();
    if (stub != null) {
      return stub.getBoundExpressionText();
    }

    return PyTypeParameter.super.getBoundExpressionText();
  }

  @Override
  @NotNull
  public PyTypeParameter.Kind getKind() {
    PyTypeParameterStub stub = getStub();

    if (stub != null) {
      return stub.getKind();
    }
    else {
      return PyTypeParameter.super.getKind();
    }
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
    PyPsiFacade facade = PyPsiFacade.getInstance(this.getProject());
    Kind kind = this.getKind();
    PyClass pyClass = switch (kind) {
      case TypeVar -> facade.createClassByQName(PyTypingTypeProvider.TYPE_VAR, this);
      case TypeVarTuple -> facade.createClassByQName(PyTypingTypeProvider.TYPE_VAR_TUPLE, this);
      case ParamSpec -> facade.createClassByQName(PyTypingTypeProvider.TYPING_PARAM_SPEC, this);
    };
    if (pyClass != null) {
      return new PyClassTypeImpl(pyClass, false);
    }
    return null;
  }
}
