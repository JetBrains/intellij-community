package com.intellij.psi.impl.source.tree;

import com.intellij.lang.Language;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.CharTable;

public class PsiCommentImpl extends LeafPsiElement implements PsiComment, JavaTokenType, PsiJavaToken {
  public PsiCommentImpl(IElementType type, char[] buffer, int startOffset, int endOffset, int lexerState, CharTable table) {
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

  public Language getLanguage() {
    PsiElement master = getNextSibling();
    if (master == null) master = getPrevSibling();
    if (master == null) master = getParent();
    return master.getLanguage();
  }
}
