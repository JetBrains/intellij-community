package com.intellij.psi.impl.compiled;

import com.intellij.psi.*;
import com.intellij.psi.impl.source.tree.TreeElement;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.openapi.diagnostic.Logger;

/**
 * @author ven
 */
public class ClsArrayInitializerMemberValueImpl extends ClsElementImpl implements PsiArrayInitializerMemberValue {
  private final ClsElementImpl myParent;
  private PsiAnnotationMemberValue[] myInitializers;
  private final static Logger LOG = Logger.getInstance("com.intellij.psi.impl.compiled.ClsArrayInitializerMemberValueImpl");

  public ClsArrayInitializerMemberValueImpl(ClsElementImpl parent) {
    myParent = parent;
  }

  public void setInitializers(PsiAnnotationMemberValue[] initializers) {
    myInitializers = initializers;
  }

  public String getText() {
    return getMirrorText();
  }

  public String getMirrorText() {
    StringBuffer buffer = new StringBuffer("{");
    for (int i = 0; i < myInitializers.length; i++) {
      buffer.append(((ClsElementImpl)myInitializers[i]).getMirrorText());
      if (i < myInitializers.length - 1) {
        buffer.append(", ");
      }
    }
    buffer.append('}');
    return buffer.toString();
  }

  public void setMirror(TreeElement element) {
    LOG.assertTrue(isValid());
    LOG.assertTrue(myMirror == null);
    myMirror = element;

    PsiArrayInitializerMemberValue mirror = (PsiArrayInitializerMemberValue)SourceTreeToPsiMap.treeElementToPsi(element);
    PsiAnnotationMemberValue[] initializers = mirror.getInitializers();
    LOG.assertTrue(myInitializers.length == initializers.length);
    for (int i = 0; i < myInitializers.length; i++) {
      ClsElementImpl value = (ClsElementImpl)myInitializers[i];
      value.setMirror(SourceTreeToPsiMap.psiElementToTree(initializers[i]));
    }
  }

  public PsiElement[] getChildren() {
    return myInitializers;
  }

  public PsiElement getParent() {
    return myParent;
  }

  public void accept(PsiElementVisitor visitor) {
    visitor.visitAnnotationArrayInitializer(this);
  }

  public PsiAnnotationMemberValue[] getInitializers() {
    return myInitializers;
  }
}
