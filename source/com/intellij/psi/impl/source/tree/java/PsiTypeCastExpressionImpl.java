package com.intellij.psi.impl.source.tree.java;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.tree.ChildRole;
import com.intellij.psi.impl.source.tree.CompositePsiElement;
import com.intellij.psi.impl.source.tree.TreeUtil;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class PsiTypeCastExpressionImpl extends CompositePsiElement implements PsiTypeCastExpression {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.tree.java.PsiTypeCastExpressionImpl");

  public PsiTypeCastExpressionImpl() {
    super(TYPE_CAST_EXPRESSION);
  }

  public PsiTypeElement getCastType() {
    return (PsiTypeElement)findChildByRoleAsPsiElement(ChildRole.TYPE);
  }

  public PsiExpression getOperand() {
    return (PsiExpression)findChildByRoleAsPsiElement(ChildRole.OPERAND);
  }

  @Nullable public PsiType getType() {
    final PsiTypeElement castType = getCastType();
    if (castType == null) return null;
    return castType.getType();
  }

  public ASTNode findChildByRole(int role) {
    LOG.assertTrue(ChildRole.isUnique(role));
    switch(role){
      default:
        return null;

      case ChildRole.LPARENTH:
        return TreeUtil.findChild(this, LPARENTH);

      case ChildRole.TYPE:
        return TreeUtil.findChild(this, TYPE);

      case ChildRole.RPARENTH:
        return TreeUtil.findChild(this, RPARENTH);

      case ChildRole.OPERAND:
        return TreeUtil.findChild(this, EXPRESSION_BIT_SET);
    }
  }

  public int getChildRole(ASTNode child) {
    assert child.getTreeParent() == this: "child:"+child+"; child.getTreeParent():"+child.getTreeParent();
    IElementType i = child.getElementType();
    if (i == LPARENTH) {
      return ChildRole.LPARENTH;
    }
    else if (i == RPARENTH) {
      return ChildRole.RPARENTH;
    }
    else if (i == TYPE) {
      return ChildRole.TYPE;
    }
    else {
      if (EXPRESSION_BIT_SET.contains(child.getElementType())) {
        return ChildRole.OPERAND;
      }
      return ChildRole.NONE;
    }
  }

  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitTypeCastExpression(this);
    }
    else {
      visitor.visitElement(this);
    }
  }

  public String toString() {
    return "PsiTypeCastExpression:" + getText();
  }
}

