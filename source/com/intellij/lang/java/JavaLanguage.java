package com.intellij.lang.java;

import com.intellij.ide.highlighter.JavaFileHighlighter;
import com.intellij.lang.Language;
import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import com.intellij.openapi.project.Project;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.PsiManager;

/**
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Jan 22, 2005
 * Time: 11:16:59 PM
 * To change this template use File | Settings | File Templates.
 */
public class JavaLanguage extends Language {
  public JavaLanguage() {
    super("JAVA");
  }

  public SyntaxHighlighter getSyntaxHighlighter(Project project) {
    LanguageLevel level = project != null ? PsiManager.getInstance(project).getEffectiveLanguageLevel() : LanguageLevel.HIGHEST;
    return new JavaFileHighlighter(level);
  }
}
