package com.intellij.psi.impl.compiled;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiAnnotationParameterList;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiNameValuePair;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.impl.source.tree.TreeElement;

/**
 * @author ven
 */
public class ClsAnnotationParameterListImpl extends ClsElementImpl implements PsiAnnotationParameterList {
  private static final Logger LOG = Logger.getInstance("com.intellij.psi.impl.compiled.ClsAnnotationParameterListImpl");

  private ClsNameValuePairImpl[] myAttributes;
  private ClsAnnotationImpl myParent;

  public ClsAnnotationParameterListImpl(ClsAnnotationImpl parent) {
    myParent = parent;
  }

  void setAttributes(ClsNameValuePairImpl[] attributes) {
    myAttributes = attributes;
  }

  public String getMirrorText() {
    if (myAttributes.length == 0) return "";

    StringBuffer buffer = new StringBuffer("(");
    for (int i = 0; i < myAttributes.length; i++) {
      buffer.append(myAttributes[i].getMirrorText());
      if (i < myAttributes.length - 1) {
        buffer.append(", ");
      }
    }

    buffer.append(")");
    return buffer.toString();
  }

  public void setMirror(TreeElement element) {
    LOG.assertTrue(isValid());
    LOG.assertTrue(myMirror == null);
    myMirror = element;

    PsiAnnotationParameterList mirror = (PsiAnnotationParameterList)SourceTreeToPsiMap.treeElementToPsi(element);
    PsiNameValuePair[] attrs = mirror.getAttributes();
    LOG.assertTrue(myAttributes.length == attrs.length);
    for (int i = 0; i < myAttributes.length; i++) {
      myAttributes[i].setMirror(SourceTreeToPsiMap.psiElementToTree(attrs[i]));
    }
  }

  public PsiElement[] getChildren() {
    return myAttributes;
  }

  public PsiElement getParent() {
    return myParent;
  }

  public void accept(PsiElementVisitor visitor) {
    visitor.visitAnnotationParameterList(this);
  }

  public PsiNameValuePair[] getAttributes() {
    return myAttributes;
  }
}
