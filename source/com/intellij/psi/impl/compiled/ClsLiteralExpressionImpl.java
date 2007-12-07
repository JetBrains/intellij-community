package com.intellij.psi.impl.compiled;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.tree.ElementType;
import com.intellij.psi.impl.source.tree.TreeElement;
import org.jetbrains.annotations.NotNull;

public class ClsLiteralExpressionImpl extends ClsElementImpl implements PsiLiteralExpression {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.compiled.ClsLiteralExpressionImpl");

  private ClsElementImpl myParent;
  private final String myText;
  private final PsiType myType;
  private final Object myValue;

  public ClsLiteralExpressionImpl(ClsElementImpl parent, String text, PsiType type, Object value) {
    myParent = parent;
    myText = text;
    myType = type;
    myValue = value;
  }

  void setParent(ClsElementImpl parent) {
    myParent = parent;
  }

  public PsiType getType() {
    return myType;
  }

  public Object getValue() {
    return myValue;
  }

  public String getText() {
    return myText;
  }

  public String getParsingError() {
    return null;
  }

  public void appendMirrorText(final int indentLevel, final StringBuffer buffer) {
    buffer.append(getText());
  }

  public void setMirror(@NotNull TreeElement element) {
    LOG.assertTrue(!CHECK_MIRROR_ENABLED || myMirror == null);
    LOG.assertTrue(element.getElementType() == ElementType.LITERAL_EXPRESSION);
    myMirror = element;
  }

  @NotNull
  public PsiElement[] getChildren() {
    return PsiElement.EMPTY_ARRAY;
  }

  public PsiElement getParent() {
    return myParent;
  }

  public String toString() {
    return "PsiLiteralExpression:" + getText();
  }

  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitLiteralExpression(this);
    }
    else {
      visitor.visitElement(this);
    }
  }
}
