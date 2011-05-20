package com.intellij.structuralsearch.duplicates;

import com.intellij.lang.Language;
import com.intellij.lang.javascript.JSTokenTypes;
import com.intellij.lang.javascript.JavascriptLanguage;
import com.intellij.lang.javascript.psi.JSExpression;
import com.intellij.lang.javascript.psi.JSFunction;
import com.intellij.lang.javascript.psi.JSStatement;
import com.intellij.lang.javascript.psi.JSVariable;
import com.intellij.lang.javascript.psi.ecmal4.JSClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.tree.TokenSet;
import org.jetbrains.annotations.NotNull;

/**
 * @author Eugene.Kudelevsky
 */
public class JSDuplicatesProfile extends SSRDuplicatesProfileBase {
  @Override
  public boolean isMyLanguage(@NotNull Language language) {
    return language.isKindOf(JavascriptLanguage.INSTANCE);
  }

  @Override
  public PsiElementRole getRole(@NotNull PsiElement element) {
    final PsiElement parent = element.getParent();

    if (parent instanceof JSVariable && ((JSVariable)parent).getNameIdentifier() == element) {
      return PsiElementRole.VARIABLE_NAME;
    }
    else if (parent instanceof JSFunction && ((JSFunction)parent).getNameIdentifier() == element) {
      return PsiElementRole.FUNCTION_NAME;
    }
    return null;
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
