package com.jetbrains.python.codeInsight.regexp;

import com.intellij.lexer.Lexer;
import com.intellij.openapi.project.Project;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiFile;
import com.intellij.psi.tree.IFileElementType;
import org.intellij.lang.regexp.RegExpFile;
import org.intellij.lang.regexp.RegExpLexer;
import org.intellij.lang.regexp.RegExpParserDefinition;
import org.jetbrains.annotations.NotNull;

/**
 * @author yole
 */
public class PythonRegexpParserDefinition extends RegExpParserDefinition {
  public static final IFileElementType PYTHON_REGEXP_FILE = new IFileElementType("PYTHON_REGEXP_FILE", PythonRegexpLanguage.INSTANCE);

  @NotNull
  public Lexer createLexer(Project project) {
    return new RegExpLexer(false, true, false);
  }

  @Override
  public IFileElementType getFileNodeType() {
    return PYTHON_REGEXP_FILE;
  }

  @Override
  public PsiFile createFile(FileViewProvider viewProvider) {
    return new RegExpFile(viewProvider, PythonRegexpLanguage.INSTANCE);
  }
}
