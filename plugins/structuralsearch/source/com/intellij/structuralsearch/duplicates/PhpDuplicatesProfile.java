package com.intellij.structuralsearch.duplicates;

import com.intellij.lang.Language;
import com.intellij.psi.PsiElement;
import com.intellij.psi.tree.TokenSet;
import com.jetbrains.php.lang.PhpLanguage;
import com.jetbrains.php.lang.lexer.PhpTokenTypes;
import com.jetbrains.php.lang.psi.elements.*;
import org.jetbrains.annotations.NotNull;

/**
 * @author Eugene.Kudelevsky
 */
public class PhpDuplicatesProfile extends SSRDuplicatesProfileBase {
  @Override
  public int getNodeCost(@NotNull PsiElement element) {
    if (element instanceof Statement || element instanceof Function) {
      return 2;
    }
    else if (element instanceof PhpExpression) {
      return 1;
    }
    return 0;
  }

  @Override
  public boolean isMyLanguage(@NotNull Language language) {
    return language.isKindOf(PhpLanguage.INSTANCE);
  }

  @Override
  public PsiElementRole getRole(@NotNull PsiElement element) {
    final PsiElement parent = element.getParent();

    if (parent instanceof Variable && ((Variable)parent).getNameIdentifier() == element) {
      return PsiElementRole.VARIABLE_NAME;
    }
    else if (parent instanceof Field && ((Field)parent).getNameIdentifier() == element) {
      return PsiElementRole.FIELD_NAME;
    }
    else if (parent instanceof Function && ((Function)parent).getNameIdentifier() == element) {
      return PsiElementRole.FUNCTION_NAME;
    }
    return null;
  }

  @Override
  public TokenSet getLiterals() {
    return PhpTokenTypes.tsCOMMON_SCALARS;
  }
}
