package com.intellij.psi.impl.compiled;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiImplUtil;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.impl.source.tree.TreeElement;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * @author ven
 */
public class ClsClassObjectAccessExpressionImpl extends ClsElementImpl implements PsiClassObjectAccessExpression {
  private static final Logger LOG = Logger.getInstance("com.intellij.psi.impl.compiled.ClsClassObjectAccessExpressionImpl");
  private final ClsTypeElementImpl myTypeElement;
  private final ClsElementImpl myParent;
  private static final @NonNls String CLASS_ENDING = ".class";

  public ClsClassObjectAccessExpressionImpl(String canonicalClassText, ClsElementImpl parent) {
    myParent = parent;
    myTypeElement = new ClsTypeElementImpl(this, canonicalClassText, ClsTypeElementImpl.VARIANCE_NONE);
  }

  public void appendMirrorText(final int indentLevel, final StringBuffer buffer) {
    myTypeElement.appendMirrorText(0, buffer);
    buffer.append(CLASS_ENDING);
  }

  public void setMirror(TreeElement element) {
    LOG.assertTrue(isValid());
    LOG.assertTrue(myMirror == null);
    myMirror = element;

    PsiClassObjectAccessExpression mirror = (PsiClassObjectAccessExpression)SourceTreeToPsiMap.treeElementToPsi(element);
      ((ClsElementImpl)getOperand()).setMirror((TreeElement)SourceTreeToPsiMap.psiElementToTree(mirror.getOperand()));
  }

  @NotNull
  public PsiElement[] getChildren() {
    return new PsiElement[]{myTypeElement};
  }

  public PsiElement getParent() {
    return myParent;
  }

  public void accept(PsiElementVisitor visitor) {
    visitor.visitClassObjectAccessExpression(this);
  }

  @NotNull
  public PsiTypeElement getOperand() {
    return myTypeElement;
  }

  public PsiType getType() {
    return PsiImplUtil.getType(this);
  }

  public String getText() {
    StringBuffer buffer = new StringBuffer();
    appendMirrorText(0, buffer);
    return buffer.toString();
  }
}
