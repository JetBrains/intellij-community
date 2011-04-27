package com.intellij.structuralsearch.extenders;

import com.intellij.lang.Language;
import com.intellij.lang.javascript.JSTokenTypes;
import com.intellij.lang.javascript.JavaScriptSupportLoader;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.tree.TokenSet;
import com.intellij.structuralsearch.JSStructuralSearchProfile;
import com.intellij.structuralsearch.StructuralSearchProfileBase;
import org.jetbrains.annotations.NotNull;

/**
 * @author Eugene.Kudelevsky
 */
public class JSStructuralSearchProfile2 extends StructuralSearchProfileBase {
  private static final TokenSet VARIABLE_DELIMETERS = TokenSet.create(JSTokenTypes.COMMA, JSTokenTypes.SEMICOLON);

  @NotNull
  @Override
  protected String[] getVarPrefixes() {
    return new String[] {"_$______"};
  }

  @NotNull
  @Override
  protected LanguageFileType getFileType() {
    return JavaScriptSupportLoader.JAVASCRIPT;
  }

  @NotNull
  @Override
  protected TokenSet getVariableDelimeters() {
    return VARIABLE_DELIMETERS;
  }

  @NotNull
  @Override
  public Language getLanguage(PsiElement element) {
    return JSStructuralSearchProfile.getLanguageForElement(element);
  }
}
