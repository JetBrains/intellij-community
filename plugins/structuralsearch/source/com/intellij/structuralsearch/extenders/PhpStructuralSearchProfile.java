package com.intellij.structuralsearch.extenders;

import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.structuralsearch.StructuralSearchProfileBase;
import com.jetbrains.php.lang.PhpFileType;
import com.jetbrains.php.lang.lexer.PhpTokenTypes;
import org.jetbrains.annotations.NotNull;

/**
 * @author Eugene.Kudelevsky
 */
public class PhpStructuralSearchProfile extends StructuralSearchProfileBase {
  private static final TokenSet VAR_DELIMITERS = TokenSet.create(PhpTokenTypes.opCOMMA, PhpTokenTypes.opSEMICOLON);

  @NotNull
  @Override
  protected String[] getVarPrefixes() {
    return new String[]{"$______", "aaaaaaaaa"};
  }

  @NotNull
  @Override
  protected LanguageFileType getFileType() {
    return PhpFileType.INSTANCE;
  }

  @Override
  public String getContext(@NotNull String pattern) {
    return "<?php $$PATTERN_PLACEHOLDER$$";
  }

  @NotNull
  @Override
  protected TokenSet getVariableDelimeters() {
    return VAR_DELIMITERS;
  }
}
