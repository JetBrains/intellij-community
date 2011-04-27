package com.intellij.structuralsearch.extenders;

import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.structuralsearch.StructuralSearchProfileBase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyFileType;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;

/**
 * @author Eugene.Kudelevsky
 */
public class GroovyStructuralSearchProfile extends StructuralSearchProfileBase {
  private static final TokenSet VARIABLE_DELIMETERS = TokenSet.create(GroovyTokenTypes.mCOMMA, GroovyTokenTypes.mSEMI);

  @NotNull
  @Override
  protected String[] getVarPrefixes() {
    return new String[] {"_$_____"};
  }

  @NotNull
  @Override
  protected LanguageFileType getFileType() {
    return GroovyFileType.GROOVY_FILE_TYPE;
  }

  @NotNull
  @Override
  protected TokenSet getVariableDelimeters() {
    return VARIABLE_DELIMETERS;
  }
}
