package com.intellij.psi.impl.source;

import com.intellij.lexer.JavaLexer;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiManager;
import org.jetbrains.annotations.NotNull;

public class PsiJavaFileImpl extends PsiJavaFileBaseImpl {
  public PsiJavaFileImpl(Project project, VirtualFile file) {
    super(project, JAVA_FILE, JAVA_FILE, file);
  }

  public PsiJavaFileImpl(Project project, String name, CharSequence text) {
    super(project, JAVA_FILE, JAVA_FILE, name, text);
  }

  public String toString(){
    return "PsiJavaFile:" + getName();
  }

  public Lexer createLexer() {
    final PsiManager manager = getManager();
    return new JavaLexer(manager.getEffectiveLanguageLevel());
  }

  @NotNull
  public FileType getFileType() {
    return StdFileTypes.JAVA;
  }
}
