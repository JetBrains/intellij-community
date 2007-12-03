package com.intellij.psi.impl.source;

import com.intellij.openapi.fileTypes.FileType;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiPlainTextFile;
import com.intellij.psi.PsiReference;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.impl.source.resolve.ResolveUtil;
import org.jetbrains.annotations.NotNull;

public class PsiPlainTextFileImpl extends PsiFileImpl implements PsiPlainTextFile{
  private final FileType myFileType;

  public PsiPlainTextFileImpl(FileViewProvider viewProvider) {
    super(PLAIN_TEXT_FILE, PLAIN_TEXT, viewProvider);
    myFileType = viewProvider.getVirtualFile().getFileType();
  }

  public void accept(@NotNull PsiElementVisitor visitor){
    visitor.visitPlainTextFile(this);
  }

  public String toString(){
    return "PsiFile(plain text):" + getName();
  }

  @NotNull
  public FileType getFileType() {
    return myFileType;
  }

  @NotNull
  public PsiReference[] getReferences() {
    return ResolveUtil.getReferencesFromProviders(this,PsiPlainTextFile.class);
  }
}
