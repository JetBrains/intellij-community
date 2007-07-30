package com.intellij.psi.impl.compiled;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiJavaToken;
import com.intellij.psi.impl.source.tree.JavaDocElementType;
import com.intellij.psi.impl.source.tree.TreeElement;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.javadoc.PsiDocTag;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

class ClsDocCommentImpl extends ClsElementImpl implements PsiDocComment, JavaTokenType, PsiJavaToken {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.compiled.ClsDocCommentImpl");

  private final ClsElementImpl myParent;

  private PsiDocTag[] myTags = null;

  public ClsDocCommentImpl(ClsElementImpl parent) {
    myParent = parent;
  }

  public void appendMirrorText(final int indentLevel, final StringBuffer buffer) {
    buffer.append("/**");
    for (PsiDocTag tag : getTags()) {
      goNextLine(indentLevel + 1, buffer);
      buffer.append("* ");
      buffer.append(tag.getText());
    }
    goNextLine(indentLevel + 1, buffer);
    buffer.append("*/");
  }

  public void setMirror(@NotNull TreeElement element) {
    LOG.assertTrue(myMirror == null);
    LOG.assertTrue(element.getElementType() == JavaDocElementType.DOC_COMMENT);
    myMirror = element;
  }

  @NotNull
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
      PsiDocTag[] tags = new PsiDocTag[1];
      tags[0] = new ClsDocTagImpl(this, "@deprecated");
      myTags = tags;
    }
    return myTags;
  }

  public PsiDocTag findTagByName(@NonNls String name) {
    if (!name.equals("deprecated")) return null;
    return getTags()[0];
  }

  public PsiDocTag[] findTagsByName(@NonNls String name) {
    if (!name.equals("deprecated")) return PsiDocTag.EMPTY_ARRAY;
    return getTags();
  }

  public IElementType getTokenType() {
    return JavaDocElementType.DOC_COMMENT;
  }

  public void accept(@NotNull PsiElementVisitor visitor) {
    visitor.visitDocComment(this);
  }

}
