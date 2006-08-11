package com.intellij.psi.impl.source.tree.java;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.impl.source.tree.ChildRole;
import com.intellij.psi.impl.source.tree.CompositePsiElement;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;

public class PsiPrefixExpressionImpl extends CompositePsiElement implements PsiPrefixExpression {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.tree.java.PsiPrefixExpressionImpl");

  public PsiPrefixExpressionImpl() {
    super(PREFIX_EXPRESSION);
  }

  public PsiExpression getOperand() {
    return (PsiExpression)findChildByRoleAsPsiElement(ChildRole.OPERAND);
  }

  @NotNull
  public PsiJavaToken getOperationSign() {
    return (PsiJavaToken)findChildByRoleAsPsiElement(ChildRole.OPERATION_SIGN);
  }

  @NotNull
  public IElementType getOperationTokenType() {
    return getOperationSign().getTokenType();
  }

  public PsiType getType() {
    PsiExpression operand = getOperand();
    if (operand == null) return null;
    PsiType type = operand.getType();
    IElementType opCode = SourceTreeToPsiMap.psiElementToTree(getOperationSign()).getElementType();
    if (opCode == PLUS || opCode == MINUS || opCode == TILDE) {
      if (type == null) return null;
      if (type == PsiType.BYTE || type == PsiType.CHAR || type == PsiType.SHORT) {
        return PsiType.INT;
      }
      else {
        return type;
      }
    }
    else if (opCode == PLUSPLUS || opCode == MINUSMINUS) {
      return type;
    }
    else if (opCode == EXCL) {
      return PsiType.BOOLEAN;
    }
    else {
      LOG.assertTrue(false);
      return null;
    }
  }

  public ASTNode findChildByRole(int role) {
    LOG.assertTrue(ChildRole.isUnique(role));
    switch(role){
      default:
        return null;

      case ChildRole.OPERATION_SIGN:
        return getFirstChildNode();

      case ChildRole.OPERAND:
        return EXPRESSION_BIT_SET.contains(getLastChildNode().getElementType()) ? getLastChildNode() : null;
    }
  }

  public int getChildRole(ASTNode child) {
    LOG.assertTrue(child.getTreeParent() == this);
    if (child == getFirstChildNode()) return ChildRole.OPERATION_SIGN;
    if (child == getLastChildNode() && EXPRESSION_BIT_SET.contains(child.getElementType())) return ChildRole.OPERAND;
    return ChildRole.NONE;
  }

  public void accept(PsiElementVisitor visitor) {
    visitor.visitPrefixExpression(this);
  }

  public String toString() {
    return "PsiPrefixExpression:" + getText();
  }
}

