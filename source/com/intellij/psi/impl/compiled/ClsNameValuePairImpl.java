package com.intellij.psi.impl.compiled;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.impl.source.tree.TreeElement;

/**
 * @author ven
 */
public class ClsNameValuePairImpl extends ClsElementImpl implements PsiNameValuePair {
  private static final Logger LOG = Logger.getInstance("com.intellij.psi.impl.compiled.ClsNameValuePairImpl");
  private ClsElementImpl myParent;
  private ClsIdentifierImpl myNameIdentifier;
  private PsiAnnotationMemberValue myMemberValue;

  public ClsNameValuePairImpl(ClsElementImpl parent) {
    myParent = parent;
  }

  void setNameIdentifier(ClsIdentifierImpl nameIdentifier) {
    myNameIdentifier = nameIdentifier;
  }

  void setMemberValue(PsiAnnotationMemberValue memberValue) {
    myMemberValue = memberValue;
  }

  public String getMirrorText() {
    return myNameIdentifier.getMirrorText() + "=" + ((ClsElementImpl)myMemberValue).getMirrorText();
  }

  public void setMirror(TreeElement element) {
    LOG.assertTrue(isValid());
    LOG.assertTrue(myMirror == null);
    myMirror = element;

    PsiNameValuePair mirror = (PsiNameValuePair)SourceTreeToPsiMap.treeElementToPsi(element);
    ((ClsElementImpl)getNameIdentifier()).setMirror(SourceTreeToPsiMap.psiElementToTree(mirror.getNameIdentifier()));
    ((ClsElementImpl)getValue()).setMirror(SourceTreeToPsiMap.psiElementToTree(mirror.getValue()));
  }

  public PsiElement[] getChildren() {
    return new PsiElement[] {myNameIdentifier, myMemberValue};
  }

  public PsiElement getParent() {
    return myParent;
  }

  public void accept(PsiElementVisitor visitor) {
    visitor.visitNameValuePair(this);
  }

  public PsiIdentifier getNameIdentifier() {
    return myNameIdentifier;
  }

  public String getName() {
    return myNameIdentifier.getText();
  }

  public PsiAnnotationMemberValue getValue() {
    return myMemberValue;
  }
}
