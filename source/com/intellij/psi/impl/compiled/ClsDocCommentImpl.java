package com.intellij.psi.impl.compiled;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
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
  private final PsiDocTag[] myTags;

  ClsDocCommentImpl(ClsElementImpl parent) {
    myParent = parent;
    
    PsiDocTag[] tags = new PsiDocTag[1];
    tags[0] = new ClsDocTagImpl(this, "@deprecated");
    myTags = tags;
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
    setMirrorCheckingType(element, JavaDocElementType.DOC_COMMENT);
  }

  @NotNull
  public PsiElement[] getChildren() {
    return getTags();
  }

  public PsiElement getParent() {
    return myParent;
  }

  public PsiElement[] getDescriptionElements() {
    return EMPTY_ARRAY;
  }

  public PsiDocTag[] getTags() {
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
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitDocComment(this);
    }
    else {
      visitor.visitElement(this);
    }
  }

}
