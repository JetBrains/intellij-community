package com.intellij.psi.impl.compiled;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.impl.source.tree.TreeElement;
import com.intellij.psi.impl.PsiImplUtil;

/**
 * @author ven
 */
public class ClsAnnotationImpl extends ClsElementImpl implements PsiAnnotation {
  public static final ClsAnnotationImpl[] EMPTY_ARRAY = new ClsAnnotationImpl[0];
  private static final Logger LOG = Logger.getInstance("com.intellij.psi.impl.compiled.ClsAnnotationImpl");
  private ClsJavaCodeReferenceElementImpl myReferenceElement;
  private ClsAnnotationParameterListImpl myParameterList;
  private ClsElementImpl myParent;

  public ClsAnnotationImpl(ClsJavaCodeReferenceElementImpl referenceElement, ClsElementImpl parent) {
    myReferenceElement = referenceElement;
    myParent = parent;
  }

  public void setParameterList(ClsAnnotationParameterListImpl parameterList) {
    myParameterList = parameterList;
  }

  public String getMirrorText() {
    StringBuffer buffer = new StringBuffer("@");
    buffer.append(myReferenceElement.getCanonicalText());
    ClsNameValuePairImpl[] attributes = (ClsNameValuePairImpl[])getParameterList().getAttributes();
    if (attributes.length > 0) {
      buffer.append("(");
      for (int i = 0; i < attributes.length; i++) {
        ClsNameValuePairImpl attribute = attributes[i];
        buffer.append(attribute.getName());
        ClsElementImpl value = ((ClsElementImpl)attribute.getValue());
        if (value != null) {
          buffer.append("=" + value.getMirrorText());
        }
        if (i < attributes.length - 1) buffer.append(",");
      }
      buffer.append(")");
    }

    return buffer.toString();
  }

  public void setMirror(TreeElement element) {
    LOG.assertTrue(isValid());
    LOG.assertTrue(myMirror == null);
    myMirror = element;

    PsiAnnotation mirror = (PsiAnnotation)SourceTreeToPsiMap.treeElementToPsi(element);
    ((ClsElementImpl)getParameterList()).setMirror(SourceTreeToPsiMap.psiElementToTree(mirror.getParameterList()));
    ((ClsElementImpl)getNameReferenceElement()).setMirror(SourceTreeToPsiMap.psiElementToTree(mirror.getNameReferenceElement()));
  }

  public PsiElement[] getChildren() {
    return new PsiElement[] {myReferenceElement, myParameterList};
  }

  public PsiElement getParent() {
    return myParent;
  }

  public void accept(PsiElementVisitor visitor) {
    visitor.visitAnnotation(this);
  }

  public PsiAnnotationParameterList getParameterList() {
    return myParameterList;
  }

  public PsiJavaCodeReferenceElement getNameReferenceElement() {
    return myReferenceElement;
  }

  public PsiAnnotationMemberValue findAttributeValue(String attributeName) {
    return PsiImplUtil.findAttributeValue(this, attributeName);
  }

  public String getText() {
    return getMirrorText();
  }
}
