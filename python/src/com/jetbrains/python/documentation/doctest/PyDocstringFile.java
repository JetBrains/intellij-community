package com.jetbrains.python.documentation.doctest;

import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiLanguageInjectionHost;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.impl.PyFileImpl;
import org.jetbrains.annotations.NotNull;

/**
 * User: ktisha
 */
public class PyDocstringFile extends PyFileImpl {

  public PyDocstringFile(FileViewProvider viewProvider) {
    super(viewProvider, PyDocstringLanguageDialect.getInstance());
  }

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
}