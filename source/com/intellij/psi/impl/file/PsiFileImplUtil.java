package com.intellij.psi.impl.file;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.impl.PsiManagerImpl;
import com.intellij.util.IncorrectOperationException;

import java.io.IOException;

public class PsiFileImplUtil {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.file.PsiFileImplUtil");

  public static PsiFile setName(final PsiFile file, String newName) throws IncorrectOperationException {
    VirtualFile vFile = file.getVirtualFile();
    PsiManagerImpl manager = (PsiManagerImpl)file.getManager();

    try{
      vFile.rename(manager, newName);
    }
    catch(IOException e){
      throw new IncorrectOperationException(e.toString());
    }
    return manager.findFile(vFile);
  }

  public static void checkSetName(PsiFile file, String name) throws IncorrectOperationException {
    VirtualFile vFile = file.getVirtualFile();
    VirtualFile parentFile = vFile.getParent();
    if (parentFile == null) return;
    VirtualFile child = parentFile.findChild(name);
    if (child != null && !child.equals(vFile)){
      throw new IncorrectOperationException("File " + child.getPresentableUrl() + " already exists.");
    }
  }

  public static void doDelete(final PsiFile file) throws IncorrectOperationException {
    final PsiManagerImpl manager = (PsiManagerImpl)file.getManager();

    final VirtualFile vFile = file.getVirtualFile();
    try{
      vFile.delete(manager);
    }
    catch(IOException e){
      throw new IncorrectOperationException(e.toString());
    }
  }

  public static PsiFile[] getPsiFilesByVirtualFiles(VirtualFile[] files, PsiManager manager) {
    PsiFile[] psiFiles = new PsiFile[files.length];
    for (int i = 0; i < files.length; i++) {
      VirtualFile file = files[i];
      PsiFile psiFile = manager.findFile(file);
      if (psiFile == null) {
        LOG.error("psiFile==null:" + file);
        continue;
      }
      psiFiles[i] = psiFile;
    }
    return psiFiles;
  }
}
