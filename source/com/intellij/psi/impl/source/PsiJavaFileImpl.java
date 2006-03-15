package com.intellij.psi.impl.source;

import com.intellij.lexer.JavaLexer;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.psi.FileViewProvider;
import org.jetbrains.annotations.NotNull;

public class PsiJavaFileImpl extends PsiJavaFileBaseImpl {
  public PsiJavaFileImpl(FileViewProvider file) {
    super(JAVA_FILE, JAVA_FILE, file);
  }

  public String toString(){
    return "PsiJavaFile:" + getName();
  }

  public Lexer createLexer() {
    return new JavaLexer(getLanguageLevel());
  }

  @NotNull
  public FileType getFileType() {
    return StdFileTypes.JAVA;
  }
}
