package com.intellij.psi.impl.compiled;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.tree.ElementType;
import com.intellij.psi.impl.source.tree.TreeElement;
import com.intellij.psi.tree.IElementType;

public class ClsPrefixExpressionImpl extends ClsElementImpl implements PsiPrefixExpression {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.compiled.ClsPrefixExpressionImpl");

  private final ClsElementImpl myParent;
  private final PsiExpression myOperand;

  private MySign mySign = null;

  public ClsPrefixExpressionImpl(ClsElementImpl parent, PsiExpression operand) {
    myParent = parent;
    myOperand = operand;
  }

  public PsiExpression getOperand() {
    return myOperand;
  }

  public PsiJavaToken getOperationSign() {
    if (mySign == null){
      mySign = new MySign();
    }
    return mySign;
  }

  public PsiType getType() {
    return myOperand.getType();
  }

  public PsiElement getParent() {
    return myParent;
  }

  public PsiElement[] getChildren() {
    return new PsiElement[]{getOperationSign(), getOperand()};
  }

  public String getText() {
    return "-" + myOperand.getText();
  }

  public String getMirrorText() {
    return getText();
  }

  public void setMirror(TreeElement element) {
    LOG.assertTrue(myMirror == null);
    LOG.assertTrue(element.getElementType() == ElementType.PREFIX_EXPRESSION);
    myMirror = element;
  }

  public void accept(PsiElementVisitor visitor) {
    visitor.visitPrefixExpression(this);
  }

  public String toString() {
    return "PsiPrefixExpression:" + getText();
  }

  private class MySign extends ClsElementImpl implements PsiJavaToken, JavaTokenType{
    public IElementType getTokenType() {
      return MINUS;
    }

    public PsiElement[] getChildren() {
      return PsiElement.EMPTY_ARRAY;
    }

    public PsiElement getParent() {
      return ClsPrefixExpressionImpl.this;
    }

    public String getMirrorText() {
      return "-";
    }

    public void setMirror(TreeElement element) {
      LOG.assertTrue(myMirror == null);
      LOG.assertTrue(element.getElementType() == ElementType.MINUS);
      myMirror = element;
    }

    public void accept(PsiElementVisitor visitor) {
      visitor.visitJavaToken(this);
    }
  }
}
