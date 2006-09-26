package com.intellij.psi.impl.compiled;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.impl.source.tree.TreeElement;
import org.jetbrains.annotations.NotNull;

/**
 * @author ven
 */
public class ClsNameValuePairImpl extends ClsElementImpl implements PsiNameValuePair {
  private static final Logger LOG = Logger.getInstance("com.intellij.psi.impl.compiled.ClsNameValuePairImpl");
  private final ClsElementImpl myParent;
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

  public void appendMirrorText(final int indentLevel, final StringBuffer buffer) {
    myNameIdentifier.appendMirrorText(0, buffer);
    buffer.append(" = ");
    ((ClsElementImpl)myMemberValue).appendMirrorText(0, buffer);
  }

  public void setMirror(TreeElement element) {
    LOG.assertTrue(isValid());
    LOG.assertTrue(myMirror == null);
    myMirror = element;

    PsiNameValuePair mirror = (PsiNameValuePair)SourceTreeToPsiMap.treeElementToPsi(element);
      ((ClsElementImpl)getNameIdentifier()).setMirror((TreeElement)SourceTreeToPsiMap.psiElementToTree(mirror.getNameIdentifier()));
      ((ClsElementImpl)getValue()).setMirror((TreeElement)SourceTreeToPsiMap.psiElementToTree(mirror.getValue()));
  }

  @NotNull
  public PsiElement[] getChildren() {
    return new PsiElement[]{myNameIdentifier, myMemberValue};
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
