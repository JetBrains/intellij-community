package com.intellij.lang.xml;

import com.intellij.psi.FileViewProviderFactory;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiManager;
import com.intellij.psi.SingleRootFileViewProvider;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.lang.Language;

/**
 * @author yole
 */
public class XmlFileViewProviderFactory implements FileViewProviderFactory {
  public FileViewProvider createFileViewProvider(final VirtualFile file, final Language language, final PsiManager manager, final boolean physical) {
    if (SingleRootFileViewProvider.isTooLarge(file)) {
      return new SingleRootFileViewProvider(manager, file, physical);
    }

    return new XmlFileViewProvider(manager, file, physical, (XMLLanguage)language);
  }
}