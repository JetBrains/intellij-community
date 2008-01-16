/*
 * @author max
 */
package com.intellij.psi;

import com.intellij.lang.Language;
import com.intellij.openapi.vfs.VirtualFile;

public class ClassFileViewProviderFactory implements FileViewProviderFactory{
  public FileViewProvider createFileViewProvider(final VirtualFile file, final Language language, final PsiManager manager, final boolean physical) {
    return new ClassFileViewProvider(manager, file, physical);
  }
}