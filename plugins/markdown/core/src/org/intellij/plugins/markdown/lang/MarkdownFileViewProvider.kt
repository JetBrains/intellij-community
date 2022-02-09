// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.lang

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
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownFile

class MarkdownFileViewProvider(manager: PsiManager, virtualFile: VirtualFile, eventSystemEnabled: Boolean)
  : MultiplePsiFilesPerDocumentFileViewProvider(manager, virtualFile, eventSystemEnabled), TemplateLanguageFileViewProvider {

  private val relevantLanguages = HashSet<Language>()

  init {
    relevantLanguages.add(baseLanguage)
    relevantLanguages.add(templateDataLanguage)
  }

  override fun createFile(lang: Language): PsiFile? {
    if (lang === MarkdownLanguage.INSTANCE) {
      return MarkdownFile(this)
    }

    val parserDefinition = LanguageParserDefinitions.INSTANCE.forLanguage(lang) ?: return null

    val psiFile = parserDefinition.createFile(this)
    if (lang === templateDataLanguage && psiFile is PsiFileImpl) {
      psiFile.contentElementType = MARKDOWN_TEMPLATE_DATA
    }

    return psiFile
  }

  override fun getBaseLanguage(): Language = MarkdownLanguage.INSTANCE

  override fun getLanguages(): Set<Language> = relevantLanguages

  override fun getTemplateDataLanguage(): Language = HTMLLanguage.INSTANCE

  override fun cloneInner(fileCopy: VirtualFile): MultiplePsiFilesPerDocumentFileViewProvider =
    MarkdownFileViewProvider(manager, fileCopy, false)
}
