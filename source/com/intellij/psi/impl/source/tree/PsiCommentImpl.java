package com.intellij.psi.impl.source.tree;

import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.CharTable;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class PsiCommentImpl extends LeafPsiElement implements PsiComment, PsiJavaToken, PsiLanguageInjectionHost {
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

  @Nullable
  public List<Pair<PsiElement, TextRange>> getInjectedPsi() {
    InjectedLanguageUtil.LiteralTextEscaper<PsiCommentImpl> escaper = null;
    if (getTokenType() == C_STYLE_COMMENT) {
      escaper = new InjectedLanguageUtil.BlockCommentTextLiteralEscaper();
    }
    else if (getTokenType() == END_OF_LINE_COMMENT) {
      escaper = new InjectedLanguageUtil.LineCommentTextLiteralEscaper();
    }

    return InjectedLanguageUtil.getInjectedPsiFiles(this, escaper);
  }
}
