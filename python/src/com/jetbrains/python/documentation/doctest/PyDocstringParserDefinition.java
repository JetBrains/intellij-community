package com.jetbrains.python.documentation.doctest;

import com.intellij.lang.PsiParser;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiLanguageInjectionHost;
import com.intellij.psi.tree.IFileElementType;
import com.intellij.psi.tree.TokenSet;
import com.jetbrains.python.PythonParserDefinition;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.impl.PyFileImpl;
import org.jetbrains.annotations.NotNull;

/**
 * User : ktisha
 */
public class PyDocstringParserDefinition extends PythonParserDefinition {
  public static final IFileElementType PYTHON_DOCSTRING_FILE = new PyDocstringFileElementType(PyDocstringLanguageDialect
                                                                                                .getInstance());

  @NotNull
  public Lexer createLexer(Project project) {
    return new PyDocstringLexer();
  }

  @NotNull
  @Override
  public PsiParser createParser(Project project) {
    return new PyDocstringParser();
  }


  @NotNull
  @Override
  public TokenSet getWhitespaceTokens() {
    return TokenSet.orSet(super.getWhitespaceTokens(), TokenSet.create(PyDocstringTokenTypes.DOTS));
  }

  @Override
  public IFileElementType getFileNodeType() {
    return PYTHON_DOCSTRING_FILE;
  }

  @Override
  public PsiFile createFile(FileViewProvider viewProvider) {
    return new PyFileImpl(viewProvider, PyDocstringLanguageDialect.getInstance()) {
      @NotNull
      @Override
      public FileType getFileType() {
        return PyDocstringFileType.INSTANCE;
      }

      @Override
      public String toString() {
        return "DocstringFile:" + getName();
      }

      @Override
      public LanguageLevel getLanguageLevel() {
        final InjectedLanguageManager languageManager = InjectedLanguageManager.getInstance(getProject());
        final PsiLanguageInjectionHost host = languageManager.getInjectionHost(this);
        if (host != null) return LanguageLevel.forElement(host.getContainingFile());
        return super.getLanguageLevel();
      }
    };
  }
}
