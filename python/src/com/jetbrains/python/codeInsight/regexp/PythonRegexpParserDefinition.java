package com.jetbrains.python.codeInsight.regexp;

import com.intellij.lang.PsiParser;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.project.Project;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiFile;
import com.intellij.psi.tree.IFileElementType;
import org.intellij.lang.regexp.*;
import org.jetbrains.annotations.NotNull;

import java.util.EnumSet;

/**
 * @author yole
 */
public class PythonRegexpParserDefinition extends RegExpParserDefinition {
  public static final IFileElementType PYTHON_REGEXP_FILE = new IFileElementType("PYTHON_REGEXP_FILE", PythonRegexpLanguage.INSTANCE);
  private final EnumSet<RegExpCapability> CAPABILITIES = EnumSet.of(RegExpCapability.DANGLING_METACHARACTERS,
                                                                    RegExpCapability.OCTAL_NO_LEADING_ZERO);

  @NotNull
  public Lexer createLexer(Project project) {
    return new RegExpLexer(CAPABILITIES);
  }

  @Override
  public PsiParser createParser(Project project) {
    return new RegExpParser(CAPABILITIES);
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
