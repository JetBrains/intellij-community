package com.intellij.psi.impl.file;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.impl.PsiManagerImpl;

/**
 * @author yole
 */
public class PsiJavaDirectoryFactory extends PsiDirectoryFactory {
  private PsiManagerImpl myManager;

  public PsiJavaDirectoryFactory(final PsiManagerImpl manager) {
    myManager = manager;
  }

  public PsiDirectory createDirectory(final VirtualFile file) {
    return new PsiJavaDirectoryImpl(myManager, file);
  }
}
