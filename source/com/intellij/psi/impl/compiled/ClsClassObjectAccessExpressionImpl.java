package com.intellij.psi.impl.compiled;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiImplUtil;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.impl.source.tree.TreeElement;

/**
 * @author ven
 */
public class ClsClassObjectAccessExpressionImpl extends ClsElementImpl implements PsiClassObjectAccessExpression {
  private static final Logger LOG = Logger.getInstance("com.intellij.psi.impl.compiled.ClsClassObjectAccessExpressionImpl");
  private ClsTypeElementImpl myTypeElement;
  private ClsElementImpl myParent;

  public ClsClassObjectAccessExpressionImpl(String canonicalClassText, ClsElementImpl parent) {
    myParent = parent;
    myTypeElement = new ClsTypeElementImpl(this, canonicalClassText, ClsTypeElementImpl.VARIANCE_NONE);
  }

  public String getMirrorText() {
    return myTypeElement.getMirrorText() + ".class";
  }

  public void setMirror(TreeElement element) {
    LOG.assertTrue(isValid());
    LOG.assertTrue(myMirror == null);
    myMirror = element;

    PsiClassObjectAccessExpression mirror = (PsiClassObjectAccessExpression)SourceTreeToPsiMap.treeElementToPsi(element);
      ((ClsElementImpl)getOperand()).setMirror((TreeElement)SourceTreeToPsiMap.psiElementToTree(mirror.getOperand()));
  }

  public PsiElement[] getChildren() {
    return new PsiElement[]{myTypeElement};
  }

  public PsiElement getParent() {
    return myParent;
  }

  public void accept(PsiElementVisitor visitor) {
    visitor.visitClassObjectAccessExpression(this);
  }

  public PsiTypeElement getOperand() {
    return myTypeElement;
  }

  public PsiType getType() {
    return PsiImplUtil.getType(this);
  }

  public String getText() {
    return getMirrorText();
  }
}
