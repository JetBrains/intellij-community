package com.intellij.psi.impl.source.tree;

import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiPlainText;
import com.intellij.psi.impl.source.Constants;
import com.intellij.util.CharTable;
import org.jetbrains.annotations.NotNull;

public class PsiPlainTextImpl extends OwnBufferLeafPsiElement implements PsiPlainText {
  protected PsiPlainTextImpl(CharSequence buffer, int startOffset, int endOffset, final CharTable table) {
    super(Constants.PLAIN_TEXT, buffer, startOffset, endOffset, table);
  }

  public void accept(@NotNull PsiElementVisitor visitor){
    visitor.visitPlainText(this);
  }

  public String toString(){
    return "PsiPlainText";
  }
}