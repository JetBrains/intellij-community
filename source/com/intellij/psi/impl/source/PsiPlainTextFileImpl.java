package com.intellij.psi.impl.source;

import com.intellij.lexer.Lexer;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiPlainTextFile;
import com.intellij.psi.PsiReference;
import com.intellij.psi.impl.source.resolve.ResolveUtil;
import org.jetbrains.annotations.NotNull;

public class PsiPlainTextFileImpl extends PsiFileImpl implements PsiPlainTextFile{
  private final FileType myFileType;

  public PsiPlainTextFileImpl(Project project, VirtualFile file) {
    super(project, PLAIN_TEXT_FILE, PLAIN_TEXT, file);
    myFileType = FileTypeManager.getInstance().getFileTypeByFile(file);
  }

  public PsiPlainTextFileImpl(Project project,
                              String name,
                              FileType fileType,
                              CharSequence text) {
    super(project, PLAIN_TEXT_FILE, PLAIN_TEXT, name, text);
    myFileType = fileType;
  }

  public void accept(PsiElementVisitor visitor){
    visitor.visitPlainTextFile(this);
  }

  public String toString(){
    return "PsiFile(plain text):" + getName();
  }

  @NotNull
  public FileType getFileType() {
    return myFileType;
  }

  public Lexer createLexer() {
    return null;
  }

  @NotNull
  public PsiReference[] getReferences() {
    return ResolveUtil.getReferencesFromProviders(this,PsiPlainTextFile.class);
  }
}
