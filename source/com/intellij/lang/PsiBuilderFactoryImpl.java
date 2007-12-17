package com.intellij.lang;

import com.intellij.lang.impl.PsiBuilderImpl;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.project.Project;

/**
 * @author yole
 */
public class PsiBuilderFactoryImpl extends PsiBuilderFactory {
  private Project myProject;

  public PsiBuilderFactoryImpl(Project project) {
    myProject = project;
  }

  public PsiBuilder createBuilder(final ASTNode tree, final Language lang, final CharSequence seq) {
    return new PsiBuilderImpl(lang, null, tree, myProject, seq);
  }

  public PsiBuilder createBuilder(final ASTNode tree, final Lexer lexer, final Language lang, final CharSequence seq) {
    return new PsiBuilderImpl(lang, lexer, tree, myProject, seq);
  }
}
