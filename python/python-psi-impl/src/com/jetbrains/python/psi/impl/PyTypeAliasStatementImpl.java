package com.jetbrains.python.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiErrorElement;
import com.intellij.util.IncorrectOperationException;
import com.jetbrains.python.PyElementTypes;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class PyTypeAliasStatementImpl extends PyElementImpl implements PyTypeAliasStatement {
  public PyTypeAliasStatementImpl(ASTNode astNode) {
    super(astNode);
  }

  @Override
  protected void acceptPyVisitor(PyElementVisitor pyVisitor) {
    pyVisitor.visitPyTypeAliasStatement(this);
  }

  @Override
  @Nullable
  public PyExpression getTypeExpression() {
    PsiElement child = getLastChild();
    while (child != null && !(child instanceof PyExpression)) {
      if (child instanceof PsiErrorElement) return null;
      child = child.getPrevSibling();
    }
    return (PyExpression)child;
  }

  @Override
  @Nullable
  public PyTypeParameterList getTypeParameterList() {
    return childToPsi(PyElementTypes.TYPE_PARAMETER_LIST);
  }

  @Override
  @Nullable
  public PsiElement getNameIdentifier() {
    ASTNode nameNode = getNode().findChildByType(PyTokenTypes.IDENTIFIER);
    return nameNode != null ? nameNode.getPsi() : null;
  }

  @Override
  public String getName() {
    PsiElement identifier = getNameIdentifier();
    return identifier != null ? identifier.getText() : null;
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
    // TODO
    return null;
  }
}
