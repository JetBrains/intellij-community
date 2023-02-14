// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.browsers

import com.intellij.ide.highlighter.XmlFileType
import com.intellij.lang.Language
import com.intellij.lang.html.HTMLLanguage
import com.intellij.lang.xhtml.XHTMLLanguage
import com.intellij.lang.xml.XMLLanguage
import com.intellij.openapi.fileTypes.FileTypeRegistry
import com.intellij.openapi.fileTypes.LanguageFileType
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.xml.util.HtmlUtil

private class WebBrowserXmlServiceImpl : WebBrowserXmlService() {
  override fun isHtmlFile(element: PsiElement): Boolean {
    return HtmlUtil.isHtmlFile(element)
  }

  override fun isHtmlFile(file: VirtualFile): Boolean {
    return HtmlUtil.isHtmlFile(file)
  }

  override fun isHtmlOrXmlFile(psiFile: PsiFile): Boolean {
    if (!isHtmlFile(psiFile.virtualFile) && !FileTypeRegistry.getInstance().isFileOfType(psiFile.virtualFile, XmlFileType.INSTANCE)) {
      return false
    }
    val baseLanguage: Language = psiFile.viewProvider.baseLanguage
    if (isHtmlOrXmlLanguage(baseLanguage)) {
      return true
    }
    return if (psiFile.fileType is LanguageFileType) isHtmlOrXmlLanguage((psiFile.fileType as LanguageFileType).language) else false
  }

  override fun isXmlLanguage(language: Language): Boolean = language == XMLLanguage.INSTANCE

  override fun isHtmlOrXmlLanguage(language: Language): Boolean {
    return language.isKindOf(HTMLLanguage.INSTANCE)
           || language === XHTMLLanguage.INSTANCE
           || language === XMLLanguage.INSTANCE
  }
}