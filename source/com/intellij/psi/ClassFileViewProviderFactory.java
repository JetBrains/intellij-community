/*
 * @author max
 */
package com.intellij.psi;

import com.intellij.lang.Language;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.fileTypes.ContentBasedClassFileProcessor;
import com.intellij.openapi.extensions.Extensions;

public class ClassFileViewProviderFactory implements FileViewProviderFactory{
  public FileViewProvider createFileViewProvider(final VirtualFile file, final Language language, final PsiManager manager, final boolean physical) {
    // Define language for compiled file
    final ContentBasedClassFileProcessor[] processors = Extensions.getExtensions(ContentBasedClassFileProcessor.EP_NAME);
    for (ContentBasedClassFileProcessor processor : processors) {
      Language lang = processor.obtainLanguageForFile(file);
      if (lang != null) {
        FileViewProviderFactory factory = LanguageFileViewProviders.INSTANCE.forLanguage(language);
        return factory.createFileViewProvider(file, language, manager, physical);
      }
    }

    return new ClassFileViewProvider(manager, file, physical);
  }
}