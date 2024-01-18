package com.jetbrains.python.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.stubs.IStubElementType;
import com.intellij.util.IncorrectOperationException;
import com.jetbrains.python.PyElementTypes;
import com.jetbrains.python.PyStubElementTypes;
import com.jetbrains.python.codeInsight.controlflow.ControlFlowCache;
import com.jetbrains.python.codeInsight.typing.PyTypingTypeProvider;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.resolve.QualifiedNameFinder;
import com.jetbrains.python.psi.stubs.PyTypeAliasStatementStub;
import com.jetbrains.python.psi.types.PyClassTypeImpl;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class PyTypeAliasStatementImpl extends PyBaseElementImpl<PyTypeAliasStatementStub> implements PyTypeAliasStatement {
  public PyTypeAliasStatementImpl(ASTNode astNode) {
    super(astNode);
  }

  public PyTypeAliasStatementImpl(PyTypeAliasStatementStub stub) {
    this(stub, PyStubElementTypes.TYPE_ALIAS_STATEMENT);
  }

  public PyTypeAliasStatementImpl(PyTypeAliasStatementStub stub, IStubElementType nodeType) {
    super(stub, nodeType);
  }


  @Override
  protected void acceptPyVisitor(PyElementVisitor pyVisitor) {
    pyVisitor.visitPyTypeAliasStatement(this);
  }

  @Override
  @Nullable
  public String getTypeExpressionText() {
    PyTypeAliasStatementStub stub = getStub();

    if (stub != null) {
      return stub.getTypeExpressionText();
    }
    else {
      PyExpression typeExpression = getTypeExpression();
      return typeExpression != null ? typeExpression.getText() : null;
    }
  }

  @Override
  @Nullable
  public PyTypeParameterList getTypeParameterList() {
    return getStubOrPsiChild(PyStubElementTypes.TYPE_PARAMETER_LIST);
  }

  @Override
  public String getName() {
    PyTypeAliasStatementStub stub = getStub();

    if (stub != null) {
      return stub.getName();
    }
    else {
      PsiElement identifier = getNameIdentifier();
      return identifier != null ? identifier.getText() : null;
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
    PyClass typeAliasTypeClass = facade.createClassByQName(PyTypingTypeProvider.TYPE_ALIAS_TYPE, this);
    if (typeAliasTypeClass != null) {
      return new PyClassTypeImpl(typeAliasTypeClass, false);
    }
    return null;
  }

  @Override
  public int getTextOffset() {
    @Nullable PsiElement name = getNameIdentifier();
    return name != null ? name.getTextOffset() : super.getTextOffset();
  }

  @Override
  public void subtreeChanged() {
    super.subtreeChanged();
    ControlFlowCache.clear(this);
  }

  @Override
  public @Nullable String getQualifiedName() {
    return QualifiedNameFinder.getQualifiedName(this);
  }
}
