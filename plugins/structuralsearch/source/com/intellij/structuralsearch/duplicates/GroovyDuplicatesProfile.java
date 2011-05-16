package com.intellij.structuralsearch.duplicates;

import com.intellij.lang.Language;
import com.intellij.psi.PsiElement;
import com.intellij.psi.tree.TokenSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyFileType;
import org.jetbrains.plugins.groovy.lang.lexer.TokenSets;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;

/**
 * @author Eugene.Kudelevsky
 */
public class GroovyDuplicatesProfile extends SSRDuplicatesProfileBase {
  @Override
  public boolean isMyLanguage(@NotNull Language language) {
    return language.isKindOf(GroovyFileType.GROOVY_LANGUAGE);
  }

  @Override
  public PsiElementRole getRole(@NotNull PsiElement element) {
    final PsiElement parent = element.getParent();

    if (parent instanceof GrVariable && ((GrVariable)parent).getNameIdentifierGroovy() == element) {
      return PsiElementRole.VARIABLE_NAME;
    }
    else if (parent instanceof GrMethod && ((GrMethod)parent).getNameIdentifierGroovy() == element) {
      return PsiElementRole.FUNCTION_NAME;
    }
    return null;
  }

  @Override
  public int getNodeCost(@NotNull PsiElement element) {
    if (element instanceof GrStatement) {
      return 2;
    }
    return 0;
  }

  @Override
  public TokenSet getLiterals() {
    return TokenSets.CONSTANTS;
  }
}