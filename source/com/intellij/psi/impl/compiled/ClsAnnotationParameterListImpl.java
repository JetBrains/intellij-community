package com.intellij.psi.impl.compiled;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.impl.source.tree.TreeElement;
import org.jetbrains.annotations.NotNull;

/**
 * @author ven
 */
public class ClsAnnotationParameterListImpl extends ClsElementImpl implements PsiAnnotationParameterList {
  private static final Logger LOG = Logger.getInstance("com.intellij.psi.impl.compiled.ClsAnnotationParameterListImpl");

  private volatile ClsNameValuePairImpl[] myAttributes;
  private final PsiAnnotation myParent;
  private static final ClsNameValuePairImpl[] EMPTY_PARAMS = new ClsNameValuePairImpl[0];

  public ClsAnnotationParameterListImpl(PsiAnnotation parent) {
    myParent = parent;
    myAttributes = EMPTY_PARAMS;
  }

  void setAttributes(ClsNameValuePairImpl[] attributes) {
    myAttributes = attributes;
  }

  public void appendMirrorText(final int indentLevel, final StringBuffer buffer) {
    if (myAttributes.length != 0) {
      buffer.append("(");
      for (int i = 0; i < myAttributes.length; i++) {
        if (i > 0) buffer.append(", ");
        myAttributes[i].appendMirrorText(indentLevel, buffer);
      }

      buffer.append(")");
    }
  }

  public void setMirror(@NotNull TreeElement element) {
    LOG.assertTrue(isValid());
    LOG.assertTrue(!CHECK_MIRROR_ENABLED || myMirror == null);
    myMirror = element;

    PsiAnnotationParameterList mirror = (PsiAnnotationParameterList)SourceTreeToPsiMap.treeElementToPsi(element);
    PsiNameValuePair[] attrs = mirror.getAttributes();
    LOG.assertTrue(myAttributes.length == attrs.length);
    for (int i = 0; i < myAttributes.length; i++) {
      myAttributes[i].setMirror((TreeElement)SourceTreeToPsiMap.psiElementToTree(attrs[i]));
    }
  }

  @NotNull
  public PsiElement[] getChildren() {
    return myAttributes;
  }

  public PsiElement getParent() {
    return myParent;
  }

  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitAnnotationParameterList(this);
    }
    else {
      visitor.visitElement(this);
    }
  }

  @NotNull
  public PsiNameValuePair[] getAttributes() {
    return myAttributes;
  }
}
