package com.intellij.psi.impl.source;

import com.intellij.lexer.JavaLexer;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.impl.PsiManagerImpl;

public class PsiJavaFileImpl extends PsiJavaFileBaseImpl {
  public PsiJavaFileImpl(PsiManagerImpl manager, VirtualFile file) {
    super(manager, JAVA_FILE, JAVA_FILE_TEXT, file);
  }

  public PsiJavaFileImpl(PsiManagerImpl manager, String name, char[] text, int startOffset, int endOffset) {
    super(manager, JAVA_FILE, JAVA_FILE_TEXT, name, text, startOffset, endOffset);
  }

  public String toString(){
    return "PsiJavaFile:" + getName();
  }

  public Lexer createLexer() {
    final PsiManager manager = getManager();
    return new JavaLexer(manager.getEffectiveLanguageLevel());
  }

  public FileType getFileType() {
    return StdFileTypes.JAVA;
  }
}
