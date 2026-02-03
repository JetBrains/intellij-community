// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.xml

import com.intellij.lang.Language
import com.intellij.lang.LanguageParserDefinitions
import com.intellij.lang.html.HTMLLanguage
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.MultiplePsiFilesPerDocumentFileViewProvider
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.impl.source.PsiFileImpl
import com.intellij.psi.templateLanguages.TemplateLanguageFileViewProvider
import org.intellij.plugins.markdown.lang.MarkdownElementTypes.MARKDOWN_TEMPLATE_DATA
import org.intellij.plugins.markdown.lang.MarkdownLanguage
import org.intellij.plugins.markdown.lang.isMarkdownLanguage
import org.intellij.plugins.markdown.lang.parser.createMarkdownFile

internal class DefaultMarkdownFileViewProvider(
  manager: PsiManager,
  file: VirtualFile,
  eventSystemEnabled: Boolean
): MultiplePsiFilesPerDocumentFileViewProvider(manager, file, eventSystemEnabled), TemplateLanguageFileViewProvider {
  private val relevantLanguages = setOf(MarkdownLanguage.INSTANCE, HTMLLanguage.INSTANCE)

  override fun createFile(language: Language): PsiFile? {
    if (language.isMarkdownLanguage()) {
      return createMarkdownFile(this)
    }
    val parserDefinition = LanguageParserDefinitions.INSTANCE.forLanguage(language) ?: return null
    val file = parserDefinition.createFile(this)
    if (language === templateDataLanguage && file is PsiFileImpl) {
      file.contentElementType = MARKDOWN_TEMPLATE_DATA
    }
    return file
  }

  override fun getBaseLanguage(): Language {
    return MarkdownLanguage.INSTANCE
  }

  override fun getLanguages(): Set<Language> {
    return relevantLanguages
  }

  override fun getTemplateDataLanguage(): Language {
    return HTMLLanguage.INSTANCE
  }

  override fun cloneInner(fileCopy: VirtualFile): MultiplePsiFilesPerDocumentFileViewProvider {
    return DefaultMarkdownFileViewProvider(manager, fileCopy, false)
  }
}
