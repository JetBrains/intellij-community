package com.intellij.structuralsearch.duplicates;

import com.intellij.lang.Language;
import com.intellij.psi.PsiElement;
import com.jetbrains.php.lang.PhpLanguage;
import com.jetbrains.php.lang.psi.elements.Function;
import com.jetbrains.php.lang.psi.elements.PhpExpression;
import com.jetbrains.php.lang.psi.elements.Statement;
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
}
