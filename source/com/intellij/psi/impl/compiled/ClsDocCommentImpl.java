package com.intellij.psi.impl.compiled;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.impl.source.tree.ElementType;
import com.intellij.psi.impl.source.tree.TreeElement;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.javadoc.PsiDocTag;

class ClsDocCommentImpl extends ClsElementImpl implements PsiDocComment, JavaTokenType{
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.compiled.ClsDocCommentImpl");

  private final ClsElementImpl myParent;

  private PsiDocTag[] myTags = null;

  public ClsDocCommentImpl(ClsElementImpl parent) {
    myParent = parent;
  }

  public String getMirrorText() {
    getTags();
    StringBuffer buffer = new StringBuffer();
    buffer.append("/**\n");
    for(int i = 0; i < myTags.length; i++){
      PsiDocTag tag = myTags[i];
      buffer.append("* ");
      buffer.append(tag.getText());
      buffer.append("\n");
    }
    buffer.append("*/");
    return buffer.toString();
  }

  public void setMirror(TreeElement element) {
    LOG.assertTrue(myMirror == null);
    LOG.assertTrue(element.getElementType() == ElementType.DOC_COMMENT);
    myMirror = element;
  }

  public PsiElement[] getChildren() {
    return getTags();
  }

  public PsiElement getParent() {
    return myParent;
  }

  public PsiElement[] getDescriptionElements() {
    return PsiElement.EMPTY_ARRAY;
  }

  public PsiDocTag[] getTags() {
    if (myTags == null){
      myTags = new PsiDocTag[1];
      myTags[0] = new ClsDocTagImpl(this, "@deprecated");
    }
    return myTags;
  }

  public PsiDocTag findTagByName(String name) {
    if (!name.equals("deprecated")) return null;
    return getTags()[0];
  }

  public PsiDocTag[] findTagsByName(String name) {
    if (!name.equals("deprecated")) return PsiDocTag.EMPTY_ARRAY;
    return getTags();
  }

  public IElementType getTokenType() {
    return JavaTokenType.DOC_COMMENT;
  }

  public void accept(PsiElementVisitor visitor) {
    visitor.visitDocComment(this);
  }

}