package com.intellij.psi.impl.source.tree;

import com.intellij.psi.JavaTokenType;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.CharTable;

public class PsiCommentImpl extends LeafPsiElement implements PsiComment, JavaTokenType {
  protected PsiCommentImpl(IElementType type, char[] buffer, int startOffset, int endOffset, int lexerState, CharTable table) {
    super(type, buffer, startOffset, endOffset, lexerState, table);
  }

  public IElementType getTokenType() {
    return getElementType();
  }

  public void accept(PsiElementVisitor visitor){
    visitor.visitComment(this);
  }

  public String toString(){
    return "PsiComment(" + getElementType().toString() + ")";
  }
}
