
package com.intellij.psi.impl.compiled;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.impl.SharedPsiElementImplUtil;
import com.intellij.psi.impl.source.tree.ElementType;
import com.intellij.psi.impl.source.tree.TreeElement;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.javadoc.PsiDocTag;
import com.intellij.psi.javadoc.PsiDocTagValue;
import com.intellij.util.IncorrectOperationException;

class ClsDocTagImpl extends ClsElementImpl implements PsiDocTag {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.compiled.ClsDocTagImpl");

  private final ClsDocCommentImpl myDocComment;
  private final PsiElement myNameElement;

  public ClsDocTagImpl(ClsDocCommentImpl docComment, String name) {
    myDocComment = docComment;
    myNameElement = new NameElement(name);
  }

  public String getMirrorText() {
    return myNameElement.getText();
  }

  public void setMirror(TreeElement element) {
    LOG.assertTrue(myMirror == null);
    LOG.assertTrue(element.getElementType() == ElementType.DOC_TAG);
    myMirror = element;
  }

  public String getText() {
    return myNameElement.getText();
  }

  public char[] textToCharArray(){
    return myNameElement.textToCharArray();
  }

  public String getName() {
    return getNameElement().getText().substring(1);
  }

  public boolean textMatches(CharSequence text) {
    return myNameElement.textMatches(text);
  }

  public boolean textMatches(PsiElement element) {
    return myNameElement.textMatches(element);
  }

  public int getTextLength(){
    return myNameElement.getTextLength();
  }

  public PsiElement[] getChildren() {
    return new PsiElement[]{myNameElement};
  }

  public PsiElement getParent() {
    return getContainingComment();
  }

  public PsiDocComment getContainingComment() {
    return myDocComment;
  }

  public PsiElement getNameElement() {
    return myNameElement;
  }

  public PsiElement[] getDataElements() {
    return PsiElement.EMPTY_ARRAY;
  }

  public PsiDocTagValue getValueElement() {
    return null;
  }

  public void accept(PsiElementVisitor visitor) {
    visitor.visitDocTag(this);
  }

  private class NameElement extends ClsElementImpl {
    private String myText;

    public NameElement(String text) {
      myText = text;
    }

    public String getText() {
      return myText;
    }

    public char[] textToCharArray(){
      return myText.toCharArray();
    }

    public PsiElement[] getChildren(){
      return PsiElement.EMPTY_ARRAY;
    }

    public String getMirrorText() {
      return null;
    }

    public void setMirror(TreeElement element) {
      myMirror = element;
    }

    public PsiElement getParent() {
      return ClsDocTagImpl.this;
    }

    public void accept(PsiElementVisitor visitor) {
      visitor.visitElement(this);
    }
  }
  public PsiElement setName(String name) throws IncorrectOperationException{
    SharedPsiElementImplUtil.setName(getNameElement(), name);
    return this;
  }
}