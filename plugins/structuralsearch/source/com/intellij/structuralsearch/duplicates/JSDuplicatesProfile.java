package com.intellij.structuralsearch.duplicates;

import com.intellij.lang.Language;
import com.intellij.lang.javascript.JSTokenTypes;
import com.intellij.lang.javascript.JavascriptLanguage;
import com.intellij.lang.javascript.psi.JSExpression;
import com.intellij.lang.javascript.psi.JSFunction;
import com.intellij.lang.javascript.psi.JSStatement;
import com.intellij.lang.javascript.psi.ecmal4.JSClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.tree.TokenSet;
import org.jetbrains.annotations.NotNull;

/**
 * @author Eugene.Kudelevsky
 */
public class JSDuplicatesProfile extends SSRDuplicatesProfile {
  @Override
  protected boolean isMyLanguage(@NotNull Language language) {
    return language.isKindOf(JavascriptLanguage.INSTANCE);
  }

  @Override
  public int getNodeCost(@NotNull PsiElement element) {
    if (element instanceof JSStatement || element instanceof JSFunction || element instanceof JSClass) {
      return 2;
    }
    else if (element instanceof JSExpression) {
      return 1;
    }
    return 0;
  }

  @Override
  public TokenSet getLiterals() {
    return JSTokenTypes.LITERALS;
  }
}
