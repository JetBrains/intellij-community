package com.jetbrains.typoscript.lang;

import com.intellij.lang.BracePair;
import com.intellij.lang.PairedBraceMatcher;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.PsiManagerEx;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtilCore;
import com.jetbrains.typoscript.lang.psi.TypoScriptFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author lene
 *         Date: 16.04.12
 */
public class TypoScriptBraceMatcher implements PairedBraceMatcher {

  private static final BracePair[] PAIRS = new BracePair[]{
    new BracePair(TypoScriptTokenTypes.MODIFICATION_OPERATOR_FUNCTION_PARAM_BEGIN,
                  TypoScriptTokenTypes.MODIFICATION_OPERATOR_FUNCTION_PARAM_END, true),
    new BracePair(TypoScriptTokenTypes.MULTILINE_VALUE_OPERATOR_BEGIN, TypoScriptTokenTypes.MULTILINE_VALUE_OPERATOR_END, false),
    new BracePair(TypoScriptTokenTypes.CODE_BLOCK_OPERATOR_BEGIN, TypoScriptTokenTypes.CODE_BLOCK_OPERATOR_END, false)
  };

  @Override
  public BracePair[] getPairs() {
    return PAIRS;
  }

  @Override
  public boolean isPairedBracesAllowedBeforeType(@NotNull IElementType lbraceType, @Nullable IElementType contextType) {
    return true;
  }

  @Override
  public int getCodeConstructStart(PsiFile file, int openingBraceOffset) {
    if (file instanceof TypoScriptFile) {
      PsiElement brace = PsiUtilCore.getElementAtOffset(file, openingBraceOffset);
      PsiElement parent = brace.getParent();
      return parent!=null? parent.getTextOffset():openingBraceOffset;
    }
    return openingBraceOffset;
  }
}
