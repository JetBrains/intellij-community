package com.intellij.structuralsearch.extenders;

import com.intellij.codeInsight.template.TemplateContextType;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiCodeFragment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.tree.TokenSet;
import com.intellij.structuralsearch.StructuralSearchProfileBase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.GroovyFileType;
import org.jetbrains.plugins.groovy.debugger.fragments.GroovyCodeFragment;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.template.GroovyTemplateContextType;

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
  protected TokenSet getVariableDelimiters() {
    return VARIABLE_DELIMETERS;
  }

  @Override
  protected PsiCodeFragment createCodeFragment(Project project, String text, @Nullable PsiElement context) {
    GroovyCodeFragment result = new GroovyCodeFragment(project, text);
    result.setContext(context);
    return result;
  }

  @Override
  public Class<? extends TemplateContextType> getTemplateContextTypeClass() {
    return GroovyTemplateContextType.class;
  }
}
