package com.intellij.psi.impl.source.tree.java;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.Constants;
import com.intellij.psi.impl.source.tree.ChildRole;
import com.intellij.psi.impl.source.tree.CompositePsiElement;
import com.intellij.psi.impl.source.tree.TreeUtil;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;

public class PsiArrayAccessExpressionImpl extends CompositePsiElement implements PsiArrayAccessExpression, Constants {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.tree.java.PsiArrayAccessExpressionImpl");

  public PsiArrayAccessExpressionImpl() {
    super(ARRAY_ACCESS_EXPRESSION);
  }

  @NotNull
  public PsiExpression getArrayExpression() {
    return (PsiExpression)findChildByRoleAsPsiElement(ChildRole.ARRAY);
  }

  public PsiExpression getIndexExpression() {
    return (PsiExpression)findChildByRoleAsPsiElement(ChildRole.INDEX);
  }

  public PsiType getType() {
    PsiType arrayType = getArrayExpression().getType();
    if (!(arrayType instanceof PsiArrayType)) return null;
    return ((PsiArrayType)arrayType).getComponentType();
  }

  public ASTNode findChildByRole(int role) {
    LOG.assertTrue(ChildRole.isUnique(role));
    switch(role){
      default:
        return null;

      case ChildRole.ARRAY:
        return getFirstChildNode();

      case ChildRole.INDEX:
        {
          ASTNode lbracket = findChildByRole(ChildRole.LBRACKET);
          if (lbracket == null) return null;
          for(ASTNode child = lbracket.getTreeNext(); child != null; child = child.getTreeNext()){
            if (EXPRESSION_BIT_SET.contains(child.getElementType())){
              return child;
            }
          }
          return null;
        }

      case ChildRole.LBRACKET:
        return TreeUtil.findChild(this, LBRACKET);

      case ChildRole.RBRACKET:
        return TreeUtil.findChild(this, RBRACKET);
    }
  }

  public int getChildRole(ASTNode child) {
    LOG.assertTrue(child.getTreeParent() == this);
    IElementType i = child.getElementType();
    if (i == LBRACKET) {
      return ChildRole.LBRACKET;
    }
    else if (i == RBRACKET) {
      return ChildRole.RBRACKET;
    }
    else {
      if (EXPRESSION_BIT_SET.contains(child.getElementType())) {
        return child == getFirstChildNode() ? ChildRole.ARRAY : ChildRole.INDEX;
      }
      else {
        return ChildRole.NONE;
      }
    }
  }

  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitArrayAccessExpression(this);
    }
    else {
      visitor.visitElement(this);
    }
  }

  public String toString() {
    return "PsiArrayAccessExpression:" + getText();
  }
}

