package com.intellij.psi.impl.source.tree.java;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiExpressionStatement;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.impl.source.tree.*;
import com.intellij.lang.ASTNode;
import org.jetbrains.annotations.NotNull;

public class PsiExpressionStatementImpl extends CompositePsiElement implements PsiExpressionStatement {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.tree.java.PsiExpressionStatementImpl");

  public PsiExpressionStatementImpl() {
    super(EXPRESSION_STATEMENT);
  }

  @NotNull
  public PsiExpression getExpression() {
    return (PsiExpression)SourceTreeToPsiMap.treeElementToPsi(TreeUtil.findChild(this, EXPRESSION_BIT_SET));
  }

  public ASTNode findChildByRole(int role) {
    LOG.assertTrue(ChildRole.isUnique(role));
    switch(role){
      default:
        return null;

      case ChildRole.EXPRESSION:
        return TreeUtil.findChild(this, EXPRESSION_BIT_SET);

      case ChildRole.CLOSING_SEMICOLON:
        return TreeUtil.findChildBackward(this, SEMICOLON);
    }
  }

  public int getChildRole(ASTNode child) {
    LOG.assertTrue(child.getTreeParent() == this);
    IElementType i = child.getElementType();
    if (i == SEMICOLON) {
      return ChildRole.CLOSING_SEMICOLON;
    }
    else {
      if (EXPRESSION_BIT_SET.contains(child.getElementType())) {
        return ChildRole.EXPRESSION;
      }
      return ChildRole.NONE;
    }
  }

  public void accept(PsiElementVisitor visitor) {
    visitor.visitExpressionStatement(this);
  }

  public String toString() {
    return "PsiExpressionStatement";
  }

  public void deleteChildInternal(ASTNode child) {
    if (getChildRole(child) == ChildRole.EXPRESSION) {
      getTreeParent().deleteChildInternal(this);
    } else {
      super.deleteChildInternal(child);
    }
  }
}
