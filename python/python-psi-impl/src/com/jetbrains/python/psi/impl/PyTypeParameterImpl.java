package com.jetbrains.python.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.psi.PyElementVisitor;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyTypeParameter;
import com.jetbrains.python.psi.PyUtil;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class PyTypeParameterImpl extends PyElementImpl implements PyTypeParameter {

  public PyTypeParameterImpl(ASTNode astNode) {
    super(astNode);
  }

  @Override
  protected void acceptPyVisitor(PyElementVisitor pyVisitor) {
    pyVisitor.visitPyTypeParameter(this);
  }

  @Override
  @Nullable
  public String getName() {
    ASTNode nameNode = getNameNode();
    return nameNode != null ? nameNode.getText() : null;
  }

  @Override
  @Nullable
  public PyExpression getBoundExpression() {
    return PsiTreeUtil.getChildOfType(this, PyExpression.class);
  }

  @Override
  @Nullable
  public ASTNode getNameNode() {
    return getNode().findChildByType(PyTokenTypes.IDENTIFIER);
  }

  @Override
  @Nullable
  public PsiElement getNameIdentifier() {
    ASTNode nameNode = getNameNode();
    return nameNode != null ? nameNode.getPsi() : null;
  }

  @Override
  public PsiElement setName(@NotNull String name) throws IncorrectOperationException {
    ASTNode oldNameElement = getNameNode();
    if (oldNameElement != null) {
      ASTNode nameElement = PyUtil.createNewName(this, name);
      getNode().replaceChild(oldNameElement, nameElement);
    }
    return this;
  }

  @Override
  @Nullable
  public PyType getType(@NotNull TypeEvalContext context, TypeEvalContext.@NotNull Key key) {
    return null;
  }
}
