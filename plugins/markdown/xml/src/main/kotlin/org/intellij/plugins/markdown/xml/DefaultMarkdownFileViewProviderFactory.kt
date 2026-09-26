package org.intellij.plugins.markdown.xml

import com.intellij.lang.Language
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.FileViewProvider
import com.intellij.psi.FileViewProviderFactory
import com.intellij.psi.PsiManager

class DefaultMarkdownFileViewProviderFactory: FileViewProviderFactory {
  override fun createFileViewProvider(
    file: VirtualFile,
    language: Language?,
    manager: PsiManager,
    eventSystemEnabled: Boolean
  ): FileViewProvider {
    return DefaultMarkdownFileViewProvider(manager, file, eventSystemEnabled)
  }
}
