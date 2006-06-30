package com.intellij.psi.impl.compiled;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiLiteralExpression;
import com.intellij.psi.PsiType;
import com.intellij.psi.impl.source.tree.ElementType;
import com.intellij.psi.impl.source.tree.TreeElement;
import org.jetbrains.annotations.NotNull;

import java.util.List;

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

  public void setMirror(TreeElement element) {
    LOG.assertTrue(myMirror == null);
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

  public void accept(PsiElementVisitor visitor) {
    visitor.visitLiteralExpression(this);
  }
}
