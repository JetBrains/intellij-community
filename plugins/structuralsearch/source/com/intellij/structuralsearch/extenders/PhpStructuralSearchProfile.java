package com.intellij.structuralsearch.extenders;

import com.intellij.codeInsight.template.TemplateContextType;
import com.intellij.lang.Language;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.structuralsearch.StructuralSearchProfileBase;
import com.jetbrains.php.lang.PhpFileType;
import com.jetbrains.php.lang.lexer.PhpTokenTypes;
import com.jetbrains.php.refactoring.PhpTemplateContextType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Eugene.Kudelevsky
 */
public class PhpStructuralSearchProfile extends StructuralSearchProfileBase {
  private static final TokenSet VAR_DELIMITERS = TokenSet.create(PhpTokenTypes.opCOMMA, PhpTokenTypes.opSEMICOLON);

  public static final String FILE_CONTEXT = "File";
  public static final String CLASS_CONTEXT = "Class";

  @NotNull
  @Override
  protected String[] getVarPrefixes() {
    return new String[]{"aaaaaaaaa", "$______"};
  }

  @NotNull
  @Override
  protected LanguageFileType getFileType() {
    return PhpFileType.INSTANCE;
  }

  @NotNull
  @Override
  public String[] getContextNames() {
    return new String[] {FILE_CONTEXT, CLASS_CONTEXT};
  }

  @Override
  public String getContext(@NotNull String pattern, @Nullable Language language, String contextName) {
    return CLASS_CONTEXT.equals(contextName) ?
           "<?php class AAAAA { $$PATTERN_PLACEHOLDER$$ }" :
           "<?php $$PATTERN_PLACEHOLDER$$";
  }

  @NotNull
  @Override
  protected TokenSet getVariableDelimiters() {
    return VAR_DELIMITERS;
  }

  @Override
  public Class<? extends TemplateContextType> getTemplateContextTypeClass() {
    return PhpTemplateContextType.class;
  }
}
